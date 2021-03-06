/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer <klaus@allpiper.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jfastnet.messages;

import com.jfastnet.MessageKey;
import com.jfastnet.MessageLog;
import com.jfastnet.events.RequestedMessageNotInLogEvent;
import com.jfastnet.processors.MessageLogProcessor;
import com.jfastnet.state.ClientState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** This message is used to request missing sequenced ids from the other side.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class RequestSeqIdsMessage extends Message implements IDontFrame {

	/** List of sequence message ids we require. */
	private List<Long> missingIds = new ArrayList<>();

	public RequestSeqIdsMessage(List<Long> missingIds, int receiverId) {
		this.missingIds = missingIds;
		setReceiverId(receiverId);
		log.info("Request absent-Ids from {}: {}", receiverId, Arrays.toString(missingIds.toArray()));
	}

	@Override
	public ReliableMode getReliableMode() {
		// If this message gets lost, the ids will be requested again from the other side.
		return ReliableMode.UNRELIABLE;
	}

	@Override
	public void process(Object context) {
		int senderId = getSenderId();
		log.info("Resend absent ids: {} to {}", Arrays.toString(missingIds.toArray()), senderId);

		int keySenderId = senderId;
		if (!getState().idProvider.resolveEveryClientMessage()) {
			// Clear sender id, if every client receives the same id for a particular message
			keySenderId = 0;
		}
		degradeNetworkQuality();
		MessageLog messageLog = getState().getProcessorOf(MessageLogProcessor.class).getMessageLog();
		for (Long absentId : missingIds) {
			MessageKey key = MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, keySenderId, absentId);
			Message message = messageLog.getSent(key);
			if (message == null) {
				log.error("Requested message {} not in log.", key);
				getState().getEventLog().add(new RequestedMessageNotInLogEvent(key, senderId));
				continue;
			}
			message.setReceiverId(senderId);
			message.setResendMessage(true);
			log.info("Resend {} to {}", message, senderId);
			getConfig().internalSender.send(message);
			getConfig().netStats.resentMessages.incrementAndGet();
		}
	}

	private void degradeNetworkQuality() {
		// If other side requests missing messages, it means we should slow down sending.
		// Thus we degrade the network quality to this peer.
		ClientState clientState = getState().getClientStates().getById(getSenderId());
		if (clientState != null) {
			clientState.getNetworkQuality().requestedMissingMessages(missingIds.size(), getConfig().timeProvider.get());
		}
	}
}
