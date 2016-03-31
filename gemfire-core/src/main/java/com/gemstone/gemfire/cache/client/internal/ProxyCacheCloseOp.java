/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.cache.client.internal;

import java.util.Properties;

import com.gemstone.gemfire.InternalGemFireError;
import com.gemstone.gemfire.cache.client.ServerConnectivityException;
import com.gemstone.gemfire.cache.client.ServerOperationException;
import com.gemstone.gemfire.distributed.internal.ServerLocation;
import com.gemstone.gemfire.internal.HeapDataOutputStream;
import com.gemstone.gemfire.internal.Version;
import com.gemstone.gemfire.internal.cache.tier.MessageType;
import com.gemstone.gemfire.internal.cache.tier.sockets.Message;
import com.gemstone.gemfire.internal.cache.tier.sockets.Part;

public class ProxyCacheCloseOp {

  public static Object executeOn(ServerLocation location, ExecutablePool pool,
      Properties securityProps, boolean keepAlive) {
    AbstractOp op = new ProxyCacheCloseOpImpl(pool, securityProps, keepAlive);
    return pool.executeOn(location, op);
  }

  private ProxyCacheCloseOp() {
    // no instances allowed
  }

  static class ProxyCacheCloseOpImpl extends AbstractOp {

    public ProxyCacheCloseOpImpl(ExecutablePool pool, Properties securityProps,
        boolean keepAlive) {
      super(MessageType.REMOVE_USER_AUTH, 1);
      getMessage().setEarlyAck(Message.MESSAGE_HAS_SECURE_PART);
      getMessage().addBytesPart(keepAlive ? new byte[] {1} : new byte[] {0});
    }

    @Override
    protected boolean needsUserId() {
      return false;
    }

    @Override
    protected void sendMessage(Connection cnx) throws Exception {
      HeapDataOutputStream hdos = new HeapDataOutputStream(Version.CURRENT);
      byte[] secureBytes = null;
      hdos.writeLong(cnx.getConnectionID());
      Object userId = UserAttributes.userAttributes.get().getServerToId().get(cnx.getServer());
      if (userId == null) {
        // This will ensure that this op is retried on another server, unless
        // the retryCount is exhausted. Fix for Bug 41501
        throw new ServerConnectivityException(
            "Connection error while authenticating user");
      }
      hdos.writeLong((Long)userId);
      try {
        secureBytes = ((ConnectionImpl)cnx).getHandShake().encryptBytes(
            hdos.toByteArray());
      } finally {
        hdos.close();
      }
      getMessage().setSecurePart(secureBytes);
      getMessage().send(false);
    }

    @Override
    protected Object processResponse(Message msg) throws Exception {
      Part part = msg.getPart(0);
      final int msgType = msg.getMessageType();
      if (msgType == MessageType.REPLY) {
        return part.getObject();
      }
      else if (msgType == MessageType.EXCEPTION) {
        String s = "While performing a remote proxy cache close";
        throw new ServerOperationException(s, (Throwable)part.getObject());
        // Get the exception toString part.
        // This was added for c++ thin client and not used in java
        // Part exceptionToStringPart = msg.getPart(1);
      }
      else if (isErrorResponse(msgType)) {
        throw new ServerOperationException(part.getString());
      }
      else {
        throw new InternalGemFireError("Unexpected message type "
            + MessageType.getString(msgType));
      }
    }

    @Override
    protected boolean isErrorResponse(int msgType) {
      return msgType == MessageType.REQUESTDATAERROR;
    }

    @Override
    protected long startAttempt(ConnectionStats stats) {
      return stats.startGet();
    }

    @Override
    protected void endSendAttempt(ConnectionStats stats, long start) {
      stats.endGetSend(start, hasFailed());
    }

    @Override
    protected void endAttempt(ConnectionStats stats, long start) {
      stats.endGet(start, hasTimedOut(), hasFailed());
    }
  }

}