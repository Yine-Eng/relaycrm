package com.relaycrm.common;

/**
 * Validation group for rules that apply when a record is updated (PUT, PATCH)
 * but not when it is created. Example: a lead's status may be omitted on
 * create (it defaults to NEW) but must be present on update.
 */
public interface OnUpdate {
}
