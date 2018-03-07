package eu.openanalytics.services;

import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;

public class LaunchingProxyListener implements ItemListener {


  @Override
  public void itemAdded(ItemEvent item) {
    System.out.println( "Item added = " + item );
  }

  @Override
  public void itemRemoved(ItemEvent item) {
    System.out.println( "Item removed = " + item );
  }
}
