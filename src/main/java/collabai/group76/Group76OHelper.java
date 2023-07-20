package collabai.group76;

import collabai.group76.util.BidUtilPair;
import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.connection.ConnectionEnd;
import geniusweb.inform.Inform;
import geniusweb.inform.OptIn;
import geniusweb.inform.OptInWithValue;
import geniusweb.inform.Settings;
import geniusweb.inform.Voting;
import geniusweb.inform.YourTurn;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.party.Capabilities;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.logging.Level;
import javax.websocket.DeploymentException;
import tudelft.utilities.logging.Reporter;

/**
 * This is the main implementation file for Group76's Agent, the Acceptance strategy is based on using AC_Combi which
 * was defined in the paper found at <a href="https://homepages.cwi.nl/~baarslag/pub/Acceptance_conditions_in_automated_negotiation.pdf"></a>
 * Our bidding Strategy is based on the on time and utility scaling with an offset of the reservation bid, we adapted
 * these parameters based on how we believe negotiations in a haggling market take place, where each person usually
 * trys to low ball the other while slowly coming closer together in utility as time goes by.
 */
public class Group76OHelper implements Group76Helper {
  //Alpha value for AC_Next
  private static final Double ALPHA = 1.0;
  //Beta value for AC_Next
  private static final Double BETA = 0.0;
  //AC_CONST initial bid to use.
  private static final Double AC_CONST = 0.90;
  //Time at which we consider concession
  private static final Double AC_TIME = 0.92;
  private static final Double RES_ALT = 0.5;
  protected ProfileInterface profileInterface;
  private PartyId partyId;
  private AllBidsList allBidsList;
  private Progress progress;
  private Profile profile;
  private String protocol;
  private Reporter reporter;
  private ConnectionEnd<Inform, Action> connection;
  private Bid lastBidReceived;
  private Bid lastBidSent;
  //Ordered by rounds so index 1 refers to round 1 etc.
  private ArrayList<BidUtilPair> receivedBidList;
  private List<Bid> sortedBidArray;
  private Double reservationBidUtility;
  private int bidsReceived;
  private int bidsMade;
  private boolean wasMyTurn;


  /**
   * Initialize the agent with the required session context. We pass in the extra parameter as we have no access to
   * them as we are using a delegator for faster development of different agents
   *
   * @param settings   The settings of our session
   * @param reporter   The logger of our session.
   * @param connection The connection variable.
   * @throws IOException
   * @throws DeploymentException
   */
  @Override
  public void init(Settings settings, Reporter reporter, ConnectionEnd<Inform, Action> connection) throws IOException, DeploymentException {
    this.partyId = settings.getID();
    this.progress = settings.getProgress();
    this.protocol = settings.getProtocol().getURI().getPath();
    this.reporter = reporter;
    this.connection = connection;
    if ("Learn".equals(protocol)) {
      getConnection().send(new LearningDone(partyId));
    } else {
      this.profileInterface = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());
    }
    Domain domain = profileInterface.getProfile().getDomain();
    this.profile = profileInterface.getProfile();
    resetDefaults(domain);
  }

  /**
   * Resets the state of our agent.
   *
   * @param domain The domain of our negotation
   */
  private void resetDefaults(Domain domain) {
    if (this.profile instanceof LinearAdditive && this.profile.getReservationBid() != null) {
      this.reservationBidUtility = ((UtilitySpace) this.profile).getUtility(profile.getReservationBid()).doubleValue();
      getReporter().log(Level.INFO, "<Group76OHelper>: Reservation bid util: " + reservationBidUtility);
    } else {
      this.reservationBidUtility = 0.6;
    }
    this.allBidsList = new AllBidsList(domain);
    this.receivedBidList = new ArrayList<>();
    List<Bid> bidList = new ArrayList<>();
    for (Bid bid : this.allBidsList) {
      bidList.add(bid);
    }
    bidList.sort(Comparator.comparingDouble(bid -> ((UtilitySpace) this.profile).getUtility(bid).doubleValue()));
    this.sortedBidArray = Collections.unmodifiableList(bidList);
    this.lastBidReceived = null;
    this.lastBidSent = null;
    this.bidsMade = 0;
    this.bidsReceived = 0;
    this.wasMyTurn = false;
  }

  /**
   * This method is called when it's our agents turn. We first check if the last offer is acceptable if not a counter
   * offer is made.
   *
   * @throws IOException
   */
  @Override
  public void myTurn() throws IOException {
    // Logging the process
    getReporter().log(Level.INFO, "<Group76OHelper>: It's my turn!");
    Action action;
    if (lastBidReceived != null && isAcceptable(lastBidReceived)) {
      getReporter().log(Level.INFO, "<Group76OHelper>: Accepted Util: " + ((UtilitySpace) profile).getUtility(this.lastBidReceived).doubleValue());
      action = new Accept(partyId, lastBidReceived);
    } else {
      Bid bid = createBid();
      action = new Offer(partyId, bid);
      lastBidSent = bid;
      getReporter().log(Level.INFO, "<Group76OHelper>: Bids made: " + ++bidsMade);
    }
    wasMyTurn = true;
    getConnection().send(action);
  }

  /**
   * Checks whether a bid is acceptable.
   * the Acceptance strategy is based on using AC_Combi which
   * was defined in the paper found at <a href="https://homepages.cwi.nl/~baarslag/pub/Acceptance_conditions_in_automated_negotiation.pdf"></a>
   * we tried the different variations of AC_Combi to see which performs best.
   *
   * @param bid The bid to check
   * @return true if we can accept
   */
  @Override
  public boolean isAcceptable(Bid bid) {
    double receivedBidUtil = ((UtilitySpace) profile).getUtility(bid).doubleValue();
    double nextBidUtility = getNextBidUtility();
    getReporter().log(Level.INFO, "<Group76OHelper>: progress: " + progress.get(System.currentTimeMillis()));
    getReporter().log(Level.INFO, "<Group76OHelper>: current round: " + ((ProgressRounds) progress).getCurrentRound());
    //Start conceding as we pass halftime else check if AC_Next ias true
    if (Group76Helper.isPastHalfTime(this.progress)) {
      int roundsToConsider = Group76Helper.getRoundsToConsider(this.progress) / 2;
      int size = receivedBidList.size();
      getReporter().log(Level.INFO, "<Group76OHelper>: rounds to consider: " + roundsToConsider);
      getReporter().log(Level.INFO, "<Group76OHelper>: list size: " + size);
      //Average utility of the bids received in the time window provided by rounds to consider
      OptionalDouble acAvg =
            this.receivedBidList.subList(size - (roundsToConsider), size).stream().mapToDouble(BidUtilPair::getUtil).average();
      //Max utility of the bids received in the time window provided by rounds to consider
      OptionalDouble acMaxW =
            this.receivedBidList.subList(size - (roundsToConsider), size).stream().mapToDouble(BidUtilPair::getUtil).max();
      //Max utility of all bids received
      OptionalDouble acMaxT = this.receivedBidList.stream().mapToDouble(BidUtilPair::getUtil).max();
      boolean acCombi = false;
      if (acMaxT.isPresent()) {
        getReporter().log(Level.INFO, "<Group76OHelper>: acMaxT: " + acMaxT.getAsDouble());
        //Checks if AC_NEXT is true or time is greater than AC_TIME and the received bid utility is higher than or
        // equal to any bid utility received before.
        acCombi =
              (Group76Helper.acNext(ALPHA, BETA, receivedBidUtil, nextBidUtility) || progress.get(System.currentTimeMillis()) > AC_TIME)
              && (receivedBidUtil >= acMaxT.getAsDouble()) && receivedBidUtil > reservationBidUtility;
        getReporter().log(Level.INFO, "<Group76OHelper>: AC_Combi: " + acCombi);
      }
      return acCombi;
    } else {
      return Group76Helper.acNext(ALPHA, BETA, receivedBidUtil, nextBidUtility);
    }
  }

  @Override
  public void setLastBid(Action action) {
    if (!wasMyTurn) {
      getReporter().log(Level.INFO, "<Group76OHelper>: Bids Received: " + ++bidsReceived);
      this.lastBidReceived = ((Offer) action).getBid();
      double lastReceivedBidUtil = ((UtilitySpace) profile).getUtility(this.lastBidReceived).doubleValue();
      receivedBidList.add(new BidUtilPair(this.lastBidReceived,
            lastReceivedBidUtil));
      getReporter().log(Level.INFO, "<Group76OHelper>: Last Recieved Bid util: " + lastReceivedBidUtil);
    } else {
      wasMyTurn = false;
    }
  }

  @Override
  public void advanceProgress(Inform info) {
    // if we get here, round must be increased.
    if (protocol == null)
      return;
    switch (protocol) {
      case "SAOP":
      case "SHAOP":
        if (!(info instanceof YourTurn))
          return;
        break;
      case "MOPAC":
        if (!(info instanceof OptIn))
          return;
        break;
      case "MOPAC2":
        if (!(info instanceof OptInWithValue))
          return;
        break;
      default:
        return;
    }
    // if we get here, round must be increased.
    if (progress instanceof ProgressRounds) {
      progress = ((ProgressRounds) progress).advance();
    }
  }


  @Override
  public Progress getProgress() {
    return this.progress;
  }

  @Override
  public Reporter getReporter() {
    return this.reporter;
  }

  @Override
  public ConnectionEnd<Inform, Action> getConnection() {
    return this.connection;
  }

  @Override
  public Capabilities getCapabilities() {
    return new Capabilities(new HashSet<>(Collections.singletonList("SAOP")), Collections.singleton(Profile.class));
  }

  @Override
  public String getDescription() {
    return "Group76OAgent uses decoupled acceptance and bidding strategies.";
  }

  @Override
  public void terminate() {
    if (this.profileInterface != null) {
      this.profileInterface.close();
      this.profileInterface = null;
    }
  }

  /**
   * Creates a bid by getting the next utility value and getting a random bid from the list of bids of utilities
   * greater than or equal to the next bid utility. If the list of possible bids is empty or has only one element
   * (prevents it from being stuck on the same bid of utility 1) find the next bid this utility less than or sent
   * utility.
   *
   * @return the bid to make
   */
  public Bid createBid() {
    List<Bid> possibleBids;
    Double nextBidUtility = getNextBidUtility();
    possibleBids = getBidsWithUtility(nextBidUtility);
    getReporter().log(Level.INFO, "<Group76OHelper>: Finding Bids of value: " + nextBidUtility);
    // If there is no bid having utility value >= acceptableUtilityValue
    if (possibleBids.size() <= 1) {
      // Getting the bid having the highest utility value
      for (int i = sortedBidArray.size() - 1; i > 0; i--) {
        if (((UtilitySpace) this.profile).getUtility(sortedBidArray.get(i)).doubleValue() <= nextBidUtility) {
          Bid maxUtilityBid = sortedBidArray.get(i);
          possibleBids.add(maxUtilityBid);
          getReporter().log(Level.INFO, "<Group76OHelper>: Found bid of utility: " + ((UtilitySpace) this.profile).getUtility(sortedBidArray.get(i)).doubleValue());
          break;
        }
      }
    }
    Collections.shuffle(possibleBids);
    return possibleBids.get(0);
  }

  /**
   * Returns the next bid utility. If this is first bid of session use AC_CONST, otherwise add a factor scaled by
   * utility and time to the reservation bid. This will prevent us from making bids of utility less than the
   * reservation bid. If reservation bid is less than RES_ALT(0.5) use RES_ALT, otherwise we concede too fast, as
   * AC_NEXT becomes true. This will reduce the weight we give to time and the utility of the opponents bids.
   *
   * @return
   */
  public Double getNextBidUtility() {
    if (lastBidReceived == null) {
      return AC_CONST;
    } else {
      if (this.reservationBidUtility < RES_ALT) {
        return ((1 - RES_ALT) / 4.0) * (1 - progress.get(System.currentTimeMillis()))
               + ((1 - RES_ALT) * (3.0 / 4.0)) * getAverageUtil()
               + RES_ALT;
      } else {
        return ((1 - reservationBidUtility) / 4.0) * (1 - progress.get(System.currentTimeMillis()))
               + ((1 - reservationBidUtility) * (3.0 / 4.0)) * getAverageUtil()
               + this.reservationBidUtility;
      }
    }
  }

  /**
   * Finds all bids of utility greater than or equal to given utility.
   *
   * @param utility The utility to use
   * @return list of possible bids.
   */
  public List<Bid> getBidsWithUtility(Double utility) {
    List<Bid> acceptableBids = new ArrayList<>();
    for (Bid bid : this.allBidsList) {
      double bidUtility = ((UtilitySpace) this.profile).getUtility(bid).doubleValue();
      if (bidUtility >= utility) {
        acceptableBids.add(bid);
      }
    }
    return acceptableBids;
  }

  /**
   * Returns the average of our last sent utility and theirs.
   *
   * @return Average utility
   */
  public Double getAverageUtil() {
    double lastSentBidUtil;
    if (lastBidSent != null) {
      lastSentBidUtil = ((UtilitySpace) profile).getUtility(lastBidSent).doubleValue();
    } else {
      lastSentBidUtil = AC_CONST;
    }
    double lastReceivedBidUtil = ((UtilitySpace) profile).getUtility(lastBidReceived).doubleValue();
    return (lastReceivedBidUtil + lastSentBidUtil) / 2.0;
  }

  @Override
  public void voting(Voting voting) throws IOException {

  }

  @Override
  public void optIn() {

  }

  @Override
  public void optInWithValue() {

  }

}
