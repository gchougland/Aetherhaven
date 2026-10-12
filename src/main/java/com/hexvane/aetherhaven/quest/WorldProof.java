package com.hexvane.aetherhaven.quest;

/**
 * The answer to a question only the world can settle, such as whether a block stands somewhere. A town's chunks are
 * loaded only near players, so the honest answer is often that nobody can tell yet.
 */
public enum WorldProof {
    YES,
    NO,
    /** The place is not loaded. Ask again when a player is near it. */
    UNKNOWN
}
