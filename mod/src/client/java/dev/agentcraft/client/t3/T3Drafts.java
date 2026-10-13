package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Drafts belong to a machine and conversation; interrupted sends require an explicit retry. */
final class T3Drafts {
	static final class Entry {
		String text = "";
		String terminalInput = "";
		transient long terminalRevision;
		List<String> files = new ArrayList<>();
		JsonObject model;
		String delivery = "queue";
		String status = "Draft saved";
		T3Api.PreparedSend prepared;
		transient boolean sending;
		void recover() {
			if (text == null) text = "";
			if (terminalInput == null) terminalInput = "";
			if (files == null) files = new ArrayList<>();
			if (delivery == null) delivery = "queue";
			if (status == null) status = "Draft saved";
			if (prepared != null || status.startsWith("Sending")) status = "Send interrupted · Enter to retry";
		}
	}
	private final Map<String, Entry> entries;
	T3Drafts(Map<String, Entry> entries) { this.entries = entries; }
	static String key(String owner, String thread, boolean fresh) { return owner + "|" + thread + (fresh ? "|new" : ""); }
	Entry get(String key) { return entries.computeIfAbsent(key, ignored -> new Entry()); }
	boolean update(String key, String text) {
		if (key == null) return false;
		Entry entry = get(key);
		if (entry.sending || entry.text.equals(text)) return false;
		entry.text = text; entry.prepared = null; entry.status = "Draft saved"; return true;
	}
	boolean begin(String key, T3Api.PreparedSend prepared) {
		Entry entry = get(key);
		if (entry.sending) return false;
		entry.prepared = prepared; entry.sending = true; entry.status = "Sending…"; return true;
	}
	void accepted(String key) {
		Entry entry = get(key);
		entry.sending = false; entry.prepared = null; entry.text = ""; entry.files.clear(); entry.model = null;
		entry.status = "Sent to T3";
	}
	void failed(String key, String error) {
		Entry entry = get(key); entry.sending = false; entry.status = "Send failed · " + error + " · Enter to retry";
	}
}
