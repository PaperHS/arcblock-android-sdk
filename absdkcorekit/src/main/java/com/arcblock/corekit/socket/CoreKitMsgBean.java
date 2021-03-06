/*
 * Copyright (c) 2017-present ArcBlock Foundation Ltd <https://www.arcblock.io/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.arcblock.corekit.socket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class CoreKitMsgBean {
	@JsonProperty
	private String topic;

	@JsonProperty
	private String event;

	@JsonProperty(value = "payload")
	private JsonNode payload;

	@JsonProperty
	private String ref;

	public CoreKitMsgBean() {

	}

	public CoreKitMsgBean(final String topic, final String event, final JsonNode payload, final String ref) {
		this.topic = topic;
		this.event = event;
		this.payload = payload;
		this.ref = ref;
	}

	public String getTopic() {
		return topic;
	}

	public String getEvent() {
		return event;
	}

	public JsonNode getPayload() {
		return payload;
	}

	/**
	 * Helper to retrieve the value of "ref" from the payload
	 *
	 * @return The ref string or null if not found
	 */
	public String getRef() {
		if (ref != null) return ref;
		final JsonNode refNode = payload.get("ref");
		return refNode != null ? refNode.textValue() : null;
	}

	/**
	 * Helper to retrieve the value of "status" from the payload
	 *
	 * @return The status string or null if not found
	 */
	public String getResponseStatus() {
		final JsonNode statusNode = payload.get("status");
		return statusNode == null ? null : statusNode.textValue();
	}

	/**
	 * Helper to retrieve the value of "reason" from the payload
	 *
	 * @return The reason string or null if not found
	 */
	public String getReason() {
		final JsonNode reasonNode = payload.get("reason");
		return reasonNode == null ? null : reasonNode.textValue();
	}

	@Override
	public String toString() {
		return "CoreKitMsgBean{" +
				"topic='" + topic + '\'' +
				", event='" + event + '\'' +
				", payload=" + payload +
				'}';
	}

}
