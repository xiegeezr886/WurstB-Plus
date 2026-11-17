/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.util;

/** Sound-specific entry point retained for existing callers. */
public final class SoundIdFilter
{
    private final RegistryIdFilter ids = new RegistryIdFilter();

    public boolean contains(String input, String soundId)
    {
        return ids.contains(input, soundId);
    }
}
