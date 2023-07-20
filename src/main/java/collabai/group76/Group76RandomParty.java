package collabai.group76;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.LearningDone;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.actions.Vote;
import geniusweb.actions.VoteWithValue;
import geniusweb.actions.Votes;
import geniusweb.actions.VotesWithValue;
import geniusweb.bidspace.AllPartialBidsList;
import geniusweb.connection.ConnectionEnd;
import geniusweb.inform.Inform;
import geniusweb.inform.OptIn;
import geniusweb.inform.OptInWithValue;
import geniusweb.inform.Settings;
import geniusweb.inform.Voting;
import geniusweb.inform.YourTurn;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import javax.websocket.DeploymentException;
import tudelft.utilities.logging.Reporter;

/**
 * Group76 Default Party, code is based on <a href="https://github.com/abyildirim/geniusweb-tutorial"></a> used for
 * testing and basic structure our actual implementation is in Group76OHelper
 */

public class Group76RandomParty implements Group76Helper {

  private final Random random = new Random();
  protected ProfileInterface profileInterface = null;
  private Bid lastReceivedBid = null;
  private PartyId partyId;
  private Progress progress;
  private Settings settings;
  private Votes lastVotes;
  private VotesWithValue lastVotesWithValue;
  private String protocol;
  private Reporter reporter;
  private ConnectionEnd<Inform, Action> connection;

  @Override
  public void init(Settings settings, Reporter reporter, ConnectionEnd<Inform, Action> connection) throws IOException,
                                                                                                                  DeploymentException {
    this.settings = settings;
    this.partyId = settings.getID();
    this.progress = settings.getProgress();
    this.protocol = settings.getProtocol().getURI().getPath();
    this.reporter = reporter;
    this.connection = connection;
    if("Learn".equals(protocol)) {
      getConnection().send(new LearningDone(partyId));
    } else {
      this.profileInterface = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());
    }
  }

  @Override
  public void myTurn() throws IOException {
    Action action;
    if((protocol.equals("SAOP") || protocol.equals("SHAOP")) && isAcceptable(lastReceivedBid)) {
      action = new Accept(partyId, lastReceivedBid);
    } else {
      AllPartialBidsList bidSpace = new AllPartialBidsList(profileInterface.getProfile().getDomain());
      Bid bid = null;
      for(int attempt = 0; attempt <20 && !isAcceptable(bid); attempt++) {
        long i = random.nextInt();
        bid = bidSpace.get(BigInteger.valueOf(i));
      }
      action = new Offer(partyId, bid);
    }
    getConnection().send(action);
  }

  @Override
  public boolean isAcceptable(Bid bid) {
    // First round: lastReceivedBid == null
    if (bid == null)
      return false;
    Profile profile;
    try {
      profile = profileInterface.getProfile();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    if (profile instanceof UtilitySpace)
      return ((UtilitySpace) profile).getUtility(bid).doubleValue() > 0.6;
    if (profile instanceof PartialOrdering) {
      return ((PartialOrdering) profile).isPreferredOrEqual(bid,
            profile.getReservationBid());
    }
    return false;
  }

  @Override
  public void setLastBid(Action action) {
    this.lastReceivedBid = ((Offer) action).getBid();
  }

  @Override
  public void advanceProgress(Inform info) {
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
  public void voting(Voting voting) throws IOException {
    switch (protocol) {
      case "MOPAC":
        this.lastVotes = vote(voting);
        getConnection().send(lastVotes);
        break;
      case "MOPAC2":
        lastVotesWithValue = voteWithValue(voting);
        getConnection().send(lastVotesWithValue);
    }
  }

  @Override
  public void optIn() {

  }

  @Override
  public void optInWithValue() {

  }

  @Override
  public Progress getProgress() {
    return progress;
  }

  @Override
  public Reporter getReporter() {
    return reporter;
  }

  @Override
  public ConnectionEnd<Inform, Action> getConnection() {
    return connection;
  }

  @Override
  public Capabilities getCapabilities() {
    return new Capabilities(new HashSet<>(Arrays.asList("SAOP", "Learn")), Collections.singleton(Profile.class));
  }

  @Override
  public String getDescription() {
    return "Group76Default offers bids having utility value greater than acceptableUtilityValue which is " +
           "a time dependent variable. Before sending the selected bid, it replaces the value of a random issue " +
           "with the issue value of the randomly selected bid from the history of the offered bids.";
  }

  @Override
  public void terminate() {
    if(this.profileInterface != null) {
      this.profileInterface.close();
      this.profileInterface = null;
    }
  }

  private Votes vote(Voting voting) {
    Object val = settings.getParameters().get("minPower");
    Integer minPower = (val instanceof Integer) ? (Integer) val : 2;
    val = settings.getParameters().get("maxPower");
    Integer maxPower = (val instanceof Integer) ? (Integer) val
                             : Integer.MAX_VALUE;

    Set<Vote> votes = voting.getOffers().stream().distinct()
          .filter(offer -> isAcceptable(offer.getBid()))
          .map(offer -> new Vote(partyId, offer.getBid(), minPower, maxPower))
          .collect(Collectors.toSet());
    return new Votes(partyId, votes);
  }

  /**
   * @param voting the {@link Voting} object containing the options
   *
   * @return our next Votes. Returns only votes on good bids and tries to
   *         distribute vote values evenly over all good bids.
   */
  private VotesWithValue voteWithValue(Voting voting) throws IOException {
    Object val = settings.getParameters().get("minPower");
    Integer minpower = (val instanceof Integer) ? (Integer) val : 2;
    val = settings.getParameters().get("maxPower");
    Integer maxpower = (val instanceof Integer) ? (Integer) val
                             : Integer.MAX_VALUE;

    List<Bid> goodbids = voting.getOffers().stream().distinct()
          .filter(offer -> isAcceptable(offer.getBid()))
          .map(offer -> offer.getBid()).collect(Collectors.toList());

    if (goodbids.isEmpty()) {
      return new VotesWithValue(partyId, Collections.emptySet());
    }
    // extra difficulty now is to have the utility sum to exactly 100
    int mostvalues = 100 / goodbids.size();
    int value = 100 - mostvalues * (goodbids.size() - 1);
    Set<VoteWithValue> votes = new HashSet<>();
    for (Bid bid : goodbids) {
      votes.add(new VoteWithValue(partyId, bid, minpower, maxpower, value));
      value = mostvalues;
    }
    return new VotesWithValue(partyId, votes);
  }

}
