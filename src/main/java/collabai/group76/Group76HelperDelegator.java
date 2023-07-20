package collabai.group76;

import geniusweb.actions.Action;
import geniusweb.connection.ConnectionEnd;
import geniusweb.inform.Inform;
import geniusweb.inform.Settings;
import geniusweb.inform.Voting;
import geniusweb.party.Capabilities;
import geniusweb.progress.Progress;
import java.io.IOException;
import javax.websocket.DeploymentException;
import tudelft.utilities.logging.Reporter;

/**
 * Group76HelperDelegator interface to allow for hotswapping of methods from different agents for faster development
 * and comparison. Each of these methods are defined in the Group76Helper interface.
 */
public class Group76HelperDelegator {

  private static final Group76Helper DEFAULT_HELPER = new Group76RandomParty();
  private static final Group76Helper O_HELPER = new Group76OHelper();

  private static final Group76Helper helper = O_HELPER;

  // Called at the beginning of the negotiation session
  static void init(Settings settings, Reporter reporter, ConnectionEnd<Inform, Action> connection) throws IOException, DeploymentException {
    helper.init(settings, reporter, connection);
  }

  //This function is called when it's our turn so that we can take an action.
  static void myTurn() throws IOException {
    helper.myTurn();
  }

  static public void terminate() {
   helper.terminate();
  }

  static void voting(Voting voting) throws IOException {
    helper.voting(voting);
  }
  static void optIn() {
    helper.optIn();
  }

  static void optInWithValue() {
    helper.optInWithValue();
  }

  static void setLastBid(Action action) {
    helper.setLastBid(action);
  }

  static void advanceProgress(Inform info) {
    helper.advanceProgress(info);
  }

  static Progress getProgress() {
    return helper.getProgress();
  }

  static Capabilities getCapabilities() {
    return helper.getCapabilities();
  }

  static String getDescription() {
    return helper.getDescription();
  }
}
