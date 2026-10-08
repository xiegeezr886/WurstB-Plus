/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

/**
 * 标记接口：该 setting 能把自己渲染进某个 GUI。
 *
 * <p>
 * 存在的理由是**反转依赖方向**。此前 {@link Setting} 直接声明
 * {@code abstract clickgui2.Component getComponent()}，于是"设置"这一层
 * 依赖"某个具体 GUI"这一层 —— 后果是删掉或替换任何一套 GUI 都会打断
 * {@code Setting} 及其 55 个子类（这是 4 套 GUI 长期无法收敛的根因）。
 * </p>
 *
 * <p>
 * 现在 {@code Setting} 只依赖这个**空接口**：它不描述组件长什么样，只描述
 * "有这么个东西"。具体 GUI 自己去实现它（{@code clickgui2.Component}
 * 就 implements 了本接口），调用方用 {@code instanceof} 收窄到自己那套类型。
 * 依赖方向因此变成 {@code clickgui2 → settings}，单向。
 * </p>
 *
 * <p>
 * 故意做成空接口：任何方法都会立刻把某套 GUI 的形状固化进"设置"层，
 * 那正是这次要解掉的东西。
 * </p>
 */
public interface SettingComponent
{
}
