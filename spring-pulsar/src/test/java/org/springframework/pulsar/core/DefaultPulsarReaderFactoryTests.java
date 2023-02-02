/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.pulsar.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.pulsar.test.support.PulsarTestContainerSupport;

/**
 * Testing {@link DefaultPulsarReaderFactory}.
 *
 * @author Soby Chacko
 */
public class DefaultPulsarReaderFactoryTests implements PulsarTestContainerSupport {

	private PulsarClient pulsarClient;

	@BeforeEach
	void createPulsarClient() throws PulsarClientException {
		this.pulsarClient = PulsarClient.builder().serviceUrl(PulsarTestContainerSupport.getPulsarBrokerUrl()).build();
	}

	@AfterEach
	void closePulsarClient() throws PulsarClientException {
		if (this.pulsarClient != null && !this.pulsarClient.isClosed()) {
			this.pulsarClient.close();
		}
	}

	@Test
	void basicPulsarReader() throws Exception {
		PulsarReaderFactory<String> pulsarReaderFactory = new DefaultPulsarReaderFactory<>(this.pulsarClient,
				Collections.emptyMap());
		Message<String> message;
		try (Reader<String> reader = pulsarReaderFactory.createReader(List.of("basic-pulsar-reader-topic"),
				MessageId.earliest, Schema.STRING)) {

			Map<String, Object> prodConfig = Map.of("topicName", "basic-pulsar-reader-topic");
			PulsarProducerFactory<String> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
					prodConfig);
			PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
			pulsarTemplate.send("hello john doe");

			message = reader.readNext();
		}
		assertThat(message.getValue()).isEqualTo("hello john doe");
	}

	@Test
	void readingFromTheMiddleOfTheTopic() throws Exception {
		PulsarReaderFactory<String> pulsarReaderFactory = new DefaultPulsarReaderFactory<>(this.pulsarClient,
				Collections.emptyMap());

		Map<String, Object> prodConfig = Map.of("topicName", "reading-from-the-middle-of-topic");
		PulsarProducerFactory<String> pulsarProducerFactory = new DefaultPulsarProducerFactory<>(pulsarClient,
				prodConfig);
		PulsarTemplate<String> pulsarTemplate = new PulsarTemplate<>(pulsarProducerFactory);
		MessageId[] messageIds = new MessageId[10];

		for (int i = 0; i < 10; i++) {
			messageIds[i] = pulsarTemplate.send("hello john doe-" + i);
		}

		Message<String> message;
		try (Reader<String> reader = pulsarReaderFactory.createReader(List.of("reading-from-the-middle-of-topic"),
				messageIds[4], Schema.STRING)) {
			for (int i = 0; i < 5; i++) {
				message = reader.readNext();
				assertThat(message.getValue()).isEqualTo("hello john doe-" + (i + 5));
			}
			message = reader.readNext(1, TimeUnit.SECONDS);
			assertThat(message).isNull();
		}
	}

}
