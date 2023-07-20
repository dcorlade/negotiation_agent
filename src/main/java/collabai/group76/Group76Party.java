package collabai.group76;

import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.inform.ActionDone;
import geniusweb.inform.Finished;
import geniusweb.inform.Inform;
import geniusweb.inform.OptIn;
import geniusweb.inform.OptInWithValue;
import geniusweb.inform.Settings;
import geniusweb.inform.Voting;
import geniusweb.inform.YourTurn;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import java.util.logging.Level;
import tudelft.utilities.logging.Reporter;


public class Group76Party extends DefaultParty {

  public Group76Party() {
  }

  public Group76Party(Reporter reporter) {
    super(reporter);
  }

  @Override
  public void notifyChange(Inform info) {
    try {
      if (info instanceof Settings) {
        Settings settings = (Settings) info;
        Group76HelperDelegator.init(settings, getReporter(), getConnection());
      } else if (info instanceof ActionDone) {
        Action action = ((ActionDone) info).getAction();
        if (action instanceof Offer) {
          Group76HelperDelegator.setLastBid(action);
        }
      } else if (info instanceof YourTurn) {
        Group76HelperDelegator.myTurn();
        Group76HelperDelegator.advanceProgress(info);
      } else if (info instanceof Finished) {
        getReporter().log(Level.INFO, "Final outcome: " + info);
        Group76HelperDelegator.terminate();
        super.terminate();
      } else if (info instanceof Voting) {
        Group76HelperDelegator.voting((Voting) info);
      } else if (info instanceof OptIn) {
        // just repeat our last vote.
        Group76HelperDelegator.optIn();
      } else if (info instanceof OptInWithValue) {
        Group76HelperDelegator.optInWithValue();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to handle info", e);
    }
  }

  @Override
  public Capabilities getCapabilities() {
    return Group76HelperDelegator.getCapabilities();
  }

  @Override
  public String getDescription() {
    return Group76HelperDelegator.getDescription();
  }



}
