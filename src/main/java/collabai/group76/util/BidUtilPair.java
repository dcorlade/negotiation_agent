package collabai.group76.util;

import geniusweb.issuevalue.Bid;

public class BidUtilPair implements Comparable<BidUtilPair>{

  Bid bid;

  Double util;

  public BidUtilPair(Bid bid, Double util) {
    this.bid = bid;
    this.util = util;
  }

  @Override
  public int compareTo(BidUtilPair o) {
    return util.compareTo(o.getUtil());
  }

  public Bid getBid() {
    return bid;
  }

  public void setBid(Bid bid) {
    this.bid = bid;
  }

  public Double getUtil() {
    return util;
  }

  public void setUtil(Double util) {
    this.util = util;
  }
}
