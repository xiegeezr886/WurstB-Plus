/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import net.wurstclient.SearchTags;

@SearchTags({"tp anchor aura", "tpanchoraura", "anchor aura tp", "如来真掌",
	"生锚打击"})
public final class TpAnchorAuraHack extends AbstractTpAnchorAuraHack
{
	public TpAnchorAuraHack()
	{
		super("TpAnchorAura", 200);
	}
}
