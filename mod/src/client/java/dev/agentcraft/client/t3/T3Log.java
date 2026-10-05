// Adapted from maxwellyoung/t3craft (MIT), copyright 2026 Maxwell Young.
// See THIRD-PARTY-NOTICES.md and licenses/t3craft-client-MIT.txt.
package dev.agentcraft.client.t3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared logger, so code used on a dedicated server never loads client-only classes. */
public final class T3Log {
	public static final Logger LOGGER = LoggerFactory.getLogger("t3craft");

	private T3Log() {
	}
}
