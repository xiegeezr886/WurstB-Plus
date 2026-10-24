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
import java.nio.file.Path;

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
		Path picked = pick(new File(args.length > 0 ? args[0] : "."));

		if(picked != null)
			System.out.println(picked);
	}

	/**
	 * Shows the picker and returns the selected file, or null when cancelled.
	 * Call this on the AWT event thread.
	 */
	public static Path pick(File initialDirectory)
	{
		SwingUtils.setLookAndFeel();
		BackgroundFileChooser fileChooser =
			new BackgroundFileChooser(initialDirectory);

		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setAcceptAllFileFilterUsed(true);
		fileChooser.setDialogTitle("选择背景图片、GIF 或视频");
		fileChooser.addChoosableFileFilter(
			new FileNameExtensionFilter("图片 / GIF / 视频",
				"png", "jpg", "jpeg", "bmp", "gif", "mp4", "m4v"));
		fileChooser.addChoosableFileFilter(
			new FileNameExtensionFilter("图片与 GIF (png, jpg, jpeg, bmp, gif)",
				"png", "jpg", "jpeg", "bmp", "gif"));
		fileChooser.addChoosableFileFilter(
			new FileNameExtensionFilter("视频 (mp4, m4v)", "mp4", "m4v"));
		fileChooser.setFileFilter(fileChooser.getChoosableFileFilters()[0]);

		if(fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
			return null;

		return fileChooser.getSelectedFile().toPath();
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
