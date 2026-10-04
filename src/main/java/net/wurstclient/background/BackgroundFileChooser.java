/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.awt.Component;
import java.awt.HeadlessException;
import java.io.File;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.wurstclient.util.SwingUtils;

/**
 * The image picker for importing a background.
 *
 * <p>
 * Like the alt list importer, this runs in its own JVM: a Swing dialog on the
 * game's thread would fight it, so this class is launched as a separate process
 * and reports the chosen path on stdout.
 */
public final class BackgroundFileChooser extends JFileChooser
{
	public static void main(String[] args)
	{
		SwingUtils.setLookAndFeel();
		BackgroundFileChooser fileChooser = new BackgroundFileChooser(
			new File(args.length > 0 ? args[0] : "."));

		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setAcceptAllFileFilterUsed(true);
		fileChooser.setDialogTitle("选择背景图片或 GIF");
		fileChooser.addChoosableFileFilter(
			new FileNameExtensionFilter("图片与 GIF (png, jpg, jpeg, bmp, gif)",
				"png", "jpg", "jpeg", "bmp", "gif"));

		if(fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
			return;

		System.out.println(fileChooser.getSelectedFile().getAbsolutePath());
	}

	public BackgroundFileChooser(File currentDirectory)
	{
		super(currentDirectory);
	}

	@Override
	protected JDialog createDialog(Component parent) throws HeadlessException
	{
		JDialog dialog = super.createDialog(parent);
		dialog.setAlwaysOnTop(true);
		return dialog;
	}
}
