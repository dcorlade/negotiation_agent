package collabai.group76;

import geniusweb.actions.Action;
import geniusweb.connection.ConnectionEnd;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.Voting;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import geniusweb.progress.ProgressTime;
import java.io.IOException;
import javax.websocket.DeploymentException;
import tudelft.utilities.logging.Reporter;

public interface Group76Helper {

  /**
   * Check if current time is past halftime.
   *
   * @param progress The progress variable to check
   * @return true if past halftime
   */
  static Boolean isPastHalfTime(Progress progress) {
    if (progress instanceof ProgressRounds) {
      ProgressRounds progressRounds = (ProgressRounds) progress;
      return progressRounds.get(System.currentTimeMillis()) > 0.5;
    } else if (progress instanceof ProgressTime) {
      ProgressTime progressTime = (ProgressTime) progress;
      return progressTime.get(System.currentTimeMillis()) > 0.5;
    }
    return false;
  }

  /**
   * Returns the amount of rounds remaining in our negotiation session. This is the window that we use for
   * calculating acMaxW which is the Max utility value of a bid in a given time range.
   *
   * @param progress The progress variable to check
   * @return Number of rounds left
   */
  static Integer getRoundsToConsider(Progress progress) {
    if (progress instanceof ProgressRounds) {
      ProgressRounds progressRounds = (ProgressRounds) progress;
      return progressRounds.getTotalRounds() - progressRounds.getCurrentRound();
    } else {
      throw new RuntimeException("Not Round Based");
    }
  }

  /**
   * * Part of the acceptance conditions for AC_Combi returns true last received bid utility(Scaled by alpha and
   * offset by beta) is greater that our next bid utility
   *
   * @param a scaling factor for the last received bid utility, range: [1, 1.02]
   * @param b offset const for the last received bid
   * @param lastReceivedUtility The utility of last received bid
   * @param nextBidUtility The utility of the bid we will send next
   * @return if acNext is true
   */
  static Boolean acNext(double a, double b, double lastReceivedUtility, double nextBidUtility) {
    return a * lastReceivedUtility + b >= nextBidUtility;
  }

  /**
   * Inits our helper with the required context vars from our party.
   * @param settings The settings of our session
   * @param reporter The logger of our session.
   * @param connection The connection variable.
   * @throws IOException
   * @throws DeploymentException
   */
  void init(Settings settings, Reporter reporter, ConnectionEnd<Inform, Action> connection) throws IOException, DeploymentException;

  /**
   * This method is called when it's our agents turn. We first check if the last offer is acceptable if not a counter
   * offer is made.
   * @throws IOException
   */
  void myTurn() throws IOException;

  /**
   * Checks whether a bid is acceptable.
   * @param bid The bid to check
   * @return true if we can accept
   */
  boolean isAcceptable(Bid bid);

  /**
   * Sets last bid received in our helper
   * @param action
   */
  void setLastBid(Action action);

  /**
   * Advance the progress of current session.
   * @param info The Inform var used to check the protocol used
   */
  void advanceProgress(Inform info);

  /**
   * Gets the progress of current session.
   * @return The progress var
   */
  Progress getProgress();

  /**
   * Gets our logger of current session.
   * @return The session logger
   */
  Reporter getReporter();

  /**
   * Gets the connection of current session.
   * @return The connection
   */
  ConnectionEnd<Inform, Action> getConnection();

  /**
   * Gets the capabilities of our agents.
   * @return The capabilities
   */
  Capabilities getCapabilities();

  /**
   * Gets the description of our agent.
   * @return The string description
   */
  String getDescription();

  /**
   * Terminates session.
   */
  void terminate();

  //Not used.
  void voting(Voting voting) throws IOException;

  //Not used.
  void optIn();

  //Not used.
  void optInWithValue();
}
