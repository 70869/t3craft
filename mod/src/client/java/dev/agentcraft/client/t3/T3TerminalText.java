package dev.agentcraft.client.t3;

import java.util.ArrayList;
import java.util.List;

/** Bounded terminal text with carriage returns, backspace and common CSI cursor/erase sequences. */
final class T3TerminalText {
	private final List<StringBuilder> lines = new ArrayList<>();
	private final StringBuilder escape = new StringBuilder();
	private int row, column;
	private boolean escaping, osc;
	private long revision;
	long revision() { return revision; }
	T3TerminalText() { lines.add(new StringBuilder()); }
	void clear() { lines.clear(); lines.add(new StringBuilder()); row = column = 0; escape.setLength(0); escaping = osc = false; revision++; }
	void append(String text) {
		for (char c : text.toCharArray()) {
			if (osc) { if (c == '\u0007' || (c == '\\' && escape.toString().endsWith("\u001b"))) { osc = false; escaping = false; escape.setLength(0); } else { if (escape.length() > 4096) escape.setLength(0); escape.append(c); } continue; }
			if (escaping) {
				escape.append(c); if (escape.length() == 1 && c == ']') { osc = true; continue; }
				if (escape.length() == 1 && c != '[') { escaping = false; escape.setLength(0); continue; }
				if (escape.length() > 1 && c >= '@' && c <= '~') { csi(escape.toString()); escaping = false; escape.setLength(0); }
				if (escape.length() > 100) { escaping = false; escape.setLength(0); } continue;
			}
			if (c == '\u001b') { escaping = true; continue; }
			if (c == '\r') { column = 0; continue; }
			if (c == '\n') { row++; column = 0; ensure(); continue; }
			if (c == '\b') { column = Math.max(0, column - 1); continue; }
			if (c == '\t') { int target = Math.min(2000, (column / 8 + 1) * 8); while (column < target) put(' '); continue; }
			if (c >= ' ') put(c);
		}
		int characters = lines.stream().mapToInt(line -> line.length() + 1).sum();
		while (characters > 120000 && lines.size() > 1) { characters -= lines.removeFirst().length() + 1; row = Math.max(0, row - 1); }
		revision++;
	}
	private void ensure() { while (lines.size() <= row) lines.add(new StringBuilder()); if (lines.size() > 2000) { int remove = lines.size() - 2000; lines.subList(0, remove).clear(); row -= remove; } }
	private void put(char c) { ensure(); if (column >= 2000) return; var line = lines.get(row); while (line.length() <= column) line.append(' '); line.setCharAt(column++, c); }
	private void csi(String sequence) {
		char op = sequence.charAt(sequence.length() - 1); String params = sequence.substring(1, sequence.length() - 1); if (params.startsWith("?")) return;
		String[] parts = params.split(";"); int first = integer(parts[0], 1);
		switch (op) {
			case 'A' -> row = Math.max(0, row - first);
			case 'B' -> { row = Math.min(1999, row + first); ensure(); }
			case 'C' -> column = Math.min(2000, column + first);
			case 'D' -> column = Math.max(0, column - first);
			case 'G' -> column = Math.max(0, Math.min(2000, first - 1));
			case 'H', 'f' -> { row = Math.max(0, Math.min(1999, first - 1)); column = parts.length > 1 ? Math.max(0, Math.min(2000, integer(parts[1], 1) - 1)) : 0; ensure(); }
			case 'J' -> { if (params.equals("2") || params.equals("3")) clear(); }
			case 'K' -> { ensure(); var line = lines.get(row); if (params.equals("2")) line.setLength(0); else if (column < line.length()) line.setLength(column); }
			default -> { }
		}
	}
	private static int integer(String value, int fallback) { try { return Math.max(1, Math.min(2000, Integer.parseInt(value))); } catch (Exception e) { return fallback; } }
	String text() { return lines.stream().map(StringBuilder::toString).collect(java.util.stream.Collectors.joining("\n")); }
}
