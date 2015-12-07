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

package com.jfastnet;

import com.jfastnet.messages.Message;
import com.jfastnet.processors.MessageLogProcessor;
import com.jfastnet.util.ConcurrentSizeLimitedMap;
import com.jfastnet.util.LRUMap;
import com.jfastnet.util.SizeLimitedList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Logs incoming and outgoing messages. Per default only reliable messages
 * get logged and an upper bound for the message log is used.
 *
 * The reliable sequence processor will grab required messages from the message
 * log. So don't change the filter unless you know what you're doing.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class MessageLog {

	@Getter
	private CircularFifoQueue<Message> received;

	@Getter
	private CircularFifoQueue<Message> sent;

	@Getter
	private LRUMap<MessageKey, Message> sentMap;

	private Config config;

	private MessageLogProcessor.ProcessorConfig processorConfig;

	public MessageLog(Config config, MessageLogProcessor.ProcessorConfig processorConfig) {
		this.config = config;
		this.processorConfig = processorConfig;
		received = new CircularFifoQueue<>(processorConfig.receivedMessagesLimit);
		sent = new CircularFifoQueue<>(processorConfig.sentMessagesLimit);
		sentMap = new LRUMap<>(processorConfig.sentMessagesMapLimit);
	}

	public synchronized void addReceived(Message message) {
		if (processorConfig.messageLogReceiveFilter.test(message)) {
			received.add(message);
		}
	}

	public synchronized void addSent(Message message) {
		if (processorConfig.messageLogSendFilter.test(message)) {
			MessageKey messageKey = MessageKey.newKey(message.getReliableMode(), message.getReceiverId(), message.getMsgId());
			if (sentMap.containsKey(messageKey)) {
				log.trace("Message already in map! Skipping!");
				return;
			}
			sent.add(message);
			log.trace("Put into sent-log: {} -- {}", messageKey, message);
			sentMap.put(messageKey, message);
		}
	}

	/** All messages are logged. */
	public static class AllMessagesPredicate implements Predicate<Message> {
		@Override
		public boolean test(Message message) {
			return true;
		}
	}

	/** No messages are logged. */
	public static class NoMessagesPredicate implements Predicate<Message> {
		@Override
		public boolean test(Message message) {
			return false;
		}
	}

	/** Only reliable messages are logged. */
	public static class ReliableMessagesPredicate implements Predicate<Message> {
		@Override
		public boolean test(Message message) {
			return !Message.ReliableMode.UNRELIABLE.equals(message.getReliableMode());
		}
	}

}
