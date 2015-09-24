/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.distributed.internal.membership.gms;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.ForcedDisconnectException;
import com.gemstone.gemfire.distributed.DistributedMember;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.distributed.internal.membership.MembershipManager;
import com.gemstone.gemfire.distributed.internal.membership.gms.interfaces.Manager;
import com.gemstone.gemfire.distributed.internal.membership.gms.mgr.GMSMembershipManager;

import dunit.DistributedTestCase;
import dunit.DistributedTestCase.WaitCriterion;

/**
 * This helper class provides access to membership manager information that
 * is not otherwise public
 * @author bruce
 * @since 5.5
 */
public class MembershipManagerHelper
{

  /** returns the JGroupMembershipManager for the given distributed system */
  public static MembershipManager getMembershipManager(DistributedSystem sys) {
    InternalDistributedSystem isys = (InternalDistributedSystem)sys;
    DistributionManager dm = (DistributionManager)isys.getDistributionManager();
    MembershipManager mgr = dm.getMembershipManager();
    return mgr;
  }
  
  /** act sick.  don't accept new connections and don't process ordered
   * messages.  Use beHealthyMember() to reverse the effects.<p>
   * Note that part of beSickMember's processing is to interrupt and
   * stop any reader threads.  A slow listener in a reader thread should
   * eat this interrupt.
   * @param sys
   */
  public static void beSickMember(DistributedSystem sys) {
    ((Manager)getMembershipManager(sys)).beSick();
  }
  
  /**
   * inhibit failure detection responses.  This can be used in conjunction
   * with beSickMember
   */
  public static void playDead(DistributedSystem sys) {
    try {
      ((Manager)getMembershipManager(sys)).playDead();
    }
    catch (CancelException e) {
      // really dead is as good as playing dead
    }
  }
  
  public static void beHealthyMember(DistributedSystem sys) {
    ((Manager)getMembershipManager(sys)).beHealthy();
  }
  
  /** returns the current coordinator address */
  public static DistributedMember getCoordinator(DistributedSystem sys) {
    return ((Manager)getMembershipManager(sys)).getCoordinator();
  }

  /** returns the current lead member address */
  public static DistributedMember getLeadMember(DistributedSystem sys) {
    return ((Manager)getMembershipManager(sys)).getLeadMember();
  }
  
  /** register a test hook with the manager */
  public static void addTestHook(DistributedSystem sys,
      com.gemstone.gemfire.distributed.internal.membership.MembershipTestHook hook) {
    getMembershipManager(sys).registerTestHook(hook);
  }
  
  /** remove a registered test hook */
  public static void removeTestHook(DistributedSystem sys,
      com.gemstone.gemfire.distributed.internal.membership.MembershipTestHook hook) {
    getMembershipManager(sys).unregisterTestHook(hook);
  }
  
//  /**
//   * returns the view lock.  Holding this lock will prevent the processing
//   * of new views, and will prevent other threads from being able to access
//   * the view
//   */
//  public static Object getViewLock(DistributedSystem sys) {
//    return getMembershipManager(sys).latestViewLock;
//  }
  
  /** returns true if the given member is shunned */
  public static boolean isShunned(DistributedSystem sys, DistributedMember mbr) {
    return ((Manager)getMembershipManager(sys)).isShunned(mbr);
  }
  
  /** returns true if the given member is a surprise member */
  public static boolean isSurpriseMember(DistributedSystem sys, DistributedMember mbr) {
    return getMembershipManager(sys).isSurpriseMember(mbr);
  }
  
  /**
   * add a member id to the surprise members set, with the given millisecond
   * clock birth time
   */
  public static void addSurpriseMember(DistributedSystem sys,
      DistributedMember mbr, long birthTime) {
    ((Manager)getMembershipManager(sys)).addSurpriseMemberForTesting(mbr, birthTime);
  }

  /**
   * inhibits/enables logging of forced-disconnect messages.
   * For quorum-lost messages this adds expected-exception annotations
   * before and after the messages to make them invisible to greplogs
   */
  public static void inhibitForcedDisconnectLogging(boolean b) {
    GMSMembershipManager.inhibitForcedDisconnectLogging(b);
  }
  
  /**
   * wait for a member to leave the view.  Throws an assertionerror
   * if the timeout period elapses before the member leaves
   */
  public static void waitForMemberDeparture(final DistributedSystem sys, 
      final DistributedMember member, final long timeout) {
    WaitCriterion ev = new WaitCriterion() {
      public boolean done() {
        return !getMembershipManager(sys).getView().contains((InternalDistributedMember)member);
      }
      public String description() {
        String assMsg = "Waited over " + timeout + " ms for " + member 
            + " to depart, but it didn't";
        return assMsg;
      }
    };
    DistributedTestCase.waitForCriterion(ev, timeout, 200, true);
  }
  
  public static void crashDistributedSystem(final DistributedSystem msys) {
    MembershipManagerHelper.inhibitForcedDisconnectLogging(true);
    MembershipManagerHelper.playDead(msys);
    GMSMembershipManager mgr = ((GMSMembershipManager)getMembershipManager(msys));
    mgr.forceDisconnect("for testing");
    while (msys.isConnected()) {
      try { Thread.sleep(1000); } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    MembershipManagerHelper.inhibitForcedDisconnectLogging(false);
  }
  
}
