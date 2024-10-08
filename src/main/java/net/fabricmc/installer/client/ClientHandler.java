/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.installer.client;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

import net.fabricmc.installer.Handler;
import net.fabricmc.installer.InstallerGui;
import net.fabricmc.installer.LoaderVersion;
import net.fabricmc.installer.launcher.MojangLauncherHelperWrapper;
import net.fabricmc.installer.util.ArgumentParser;
import net.fabricmc.installer.util.InstallerData;
import net.fabricmc.installer.util.InstallerProgress;
import net.fabricmc.installer.util.NoopCaret;
import net.fabricmc.installer.util.Reference;
import net.fabricmc.installer.util.Utils;

public class ClientHandler extends Handler {
	private JTextField profileInstallLocation;
	private JButton selectProfileFolderButton;

	@Override
	public String name() {
		return "Client";
	}

	@Override
	public void install() {
		if (MojangLauncherHelperWrapper.isMojangLauncherOpen()) {
			showLauncherOpenMessage();
			return;
		}

		doInstall();
	}

	private void doInstall() {
		String gameVersion = Utils.INSTALLER_DATA.minecraftVersion;
		LoaderVersion loaderVersion = queryLoaderVersion();
		if (loaderVersion == null) return;

		System.out.println("Installing");

		new Thread(() -> {
			try {
				updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.installing")).format(new Object[]{loaderVersion.name}));
				Path mcPath = Paths.get(installLocation.getText());

				if (!Files.exists(mcPath)) {
					throw new RuntimeException(Utils.BUNDLE.getString("progress.exception.no.launcher.directory"));
				}

				final ProfileInstaller profileInstaller = new ProfileInstaller(mcPath);
				ProfileInstaller.LauncherType launcherType = null;

				if (Utils.INSTALLER_DATA.createProfile) {
					List<ProfileInstaller.LauncherType> types = profileInstaller.getInstalledLauncherTypes();

					if (types.size() == 0) {
						throw new RuntimeException(Utils.BUNDLE.getString("progress.exception.no.launcher.profile"));
					} else if (types.size() == 1) {
						launcherType = types.get(0);
					} else {
						launcherType = showLauncherTypeSelection();

						if (launcherType == null) {
							// canceled
							statusLabel.setText(Utils.BUNDLE.getString("prompt.ready.install"));
							return;
						}
					}
				}

				String versionName = ClientInstaller.install(mcPath, gameVersion, loaderVersion, this);
				String profileName = Reference.INSTALLER_NAME + "-" + gameVersion;

				if (Utils.INSTALLER_DATA.createProfile) {
					if (launcherType == null) {
						throw new RuntimeException(Utils.BUNDLE.getString("progress.exception.no.launcher.profile"));
					}

					Path profileGameDir = Paths.get(profileInstallLocation.getText());
					profileInstaller.setupProfile(versionName, profileName, gameVersion, launcherType, profileGameDir);

					Path modsDir = profileGameDir.resolve("mods");

					List<InstallerData.ModData> modsToInstall = new ArrayList<>();
					Set<String> modIds = new HashSet<>();

					for (InstallerData.ModData modData : Utils.INSTALLER_DATA.mods) {
						if (!modData.enabled) {
							continue;
						}

						if (this.checkBoxes.get(modData).isSelected()) {
							modsToInstall.add(modData);
							modIds.add(modData.modId);
						}
					}

					// Remove old mods if they exist
					Utils.removeMods(modsDir, modIds);

					// Download mods
					for (InstallerData.ModData modData : modsToInstall) {
						Utils.downloadMod(modsDir, modData, this);
					}

					Path serversPath = profileGameDir.resolve("servers.dat");

					if (Files.notExists(serversPath)) {
						System.out.println("Creating default servers.dat");

						try (InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream("servers.dat")) {
							if (stream == null) {
								throw new FileNotFoundException();
							}

							Files.copy(stream, serversPath);
						}
					}
				}

				updateProgress(Utils.BUNDLE.getString("progress.done"));

				SwingUtilities.invokeLater(() -> showInstalledMessage(loaderVersion.name, gameVersion, profileName, mcPath.resolve("mods")));
			} catch (Exception e) {
				error(e);
			} finally {
				buttonInstall.setEnabled(true);
			}
		}).start();
	}

	private void showInstalledMessage(String loaderVersion, String gameVersion, String profileName, Path modsDirectory) {
		String content = new MessageFormat(Utils.BUNDLE.getString("prompt.install.successful")).format(new Object[]{loaderVersion, gameVersion, profileName});
		JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + buildEditorPaneStyle() + "\">" + content + "</body></html>");
		pane.setMaximumSize(new Dimension(100, 500));
		pane.setBackground(new Color(0, 0, 0, 0));
		pane.setEditable(false);
		pane.setCaret(new NoopCaret());

		pane.addHyperlinkListener(e -> {
			try {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if (e.getDescription().equals("fabric://mods")) {
						Desktop.getDesktop().open(modsDirectory.toFile());
					} else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
						Desktop.getDesktop().browse(e.getURL().toURI());
					} else {
						throw new UnsupportedOperationException("Failed to open " + e.getURL().toString());
					}
				}
			} catch (Throwable throwable) {
				error(throwable);
			}
		});

		final Image iconImage = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png"));
		JOptionPane.showMessageDialog(
				null,
				pane,
				Utils.BUNDLE.getString("prompt.install.successful.title"),
				JOptionPane.INFORMATION_MESSAGE,
				new ImageIcon(iconImage.getScaledInstance(64, 64, Image.SCALE_DEFAULT))
		);
	}

	private ProfileInstaller.LauncherType showLauncherTypeSelection() {
		Object[] options = { Utils.BUNDLE.getString("prompt.launcher.type.xbox"), Utils.BUNDLE.getString("prompt.launcher.type.win32")};

		int result = JOptionPane.showOptionDialog(null,
				Utils.BUNDLE.getString("prompt.launcher.type.body"),
				Utils.BUNDLE.getString("installer.title"),
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]
				);

		if (result == JOptionPane.CLOSED_OPTION) {
			return null;
		}

		return result == JOptionPane.YES_OPTION ? ProfileInstaller.LauncherType.MICROSOFT_STORE : ProfileInstaller.LauncherType.WIN32;
	}

	private void showLauncherOpenMessage() {
		int result = JOptionPane.showConfirmDialog(null, Utils.BUNDLE.getString("prompt.launcher.open.body"), Utils.BUNDLE.getString("prompt.launcher.open.tile"), JOptionPane.YES_NO_OPTION);

		if (result == JOptionPane.YES_OPTION) {
			doInstall();
		} else {
			buttonInstall.setEnabled(true);
		}
	}

	@Override
	public void installCli(ArgumentParser args) throws Exception {
		Path path = Paths.get(args.getOrDefault("dir", () -> Utils.findDefaultInstallDir().toString()));

		if (!Files.exists(path)) {
			throw new FileNotFoundException("Launcher directory not found at " + path);
		}

		String gameVersion = getGameVersion(args);
		LoaderVersion loaderVersion = new LoaderVersion(getLoaderVersion(args));

		String profileName = ClientInstaller.install(path, gameVersion, loaderVersion, InstallerProgress.CONSOLE);

		if (args.has("noprofile")) {
			return;
		}

		ProfileInstaller profileInstaller = new ProfileInstaller(path);
		List<ProfileInstaller.LauncherType> types = profileInstaller.getInstalledLauncherTypes();
		ProfileInstaller.LauncherType launcherType = null;

		if (args.has("launcher")) {
			launcherType = ProfileInstaller.LauncherType.valueOf(args.get("launcher").toUpperCase(Locale.ROOT));
		}

		if (launcherType == null) {
			if (types.size() == 0) {
				throw new FileNotFoundException("Could not find a valid launcher profile .json");
			} else if (types.size() == 1) {
				// Only 1 launcher type found, install to that.
				launcherType = types.get(0);
			} else {
				throw new FileNotFoundException("Multiple launcher installations were found, please specify the target launcher using -launcher");
			}
		}

		// profileInstaller.setupProfile(profileName, gameVersion, launcherType);
		throw new UnsupportedOperationException("Please use the GUI");
	}

	@Override
	public String cliHelp() {
		return "-dir <install dir> -mcversion <minecraft version, default latest> -loader <loader version, default latest> -launcher [win32, microsoft_store]";
	}

	@Override
	public void setupPane2(JPanel pane, GridBagConstraints c, InstallerGui installerGui) {
		addRow(pane, c, "prompt.select.location.profile",
				profileInstallLocation = new JTextField(20),
				selectProfileFolderButton = new JButton());
		selectProfileFolderButton.setText("...");
		selectProfileFolderButton.setPreferredSize(new Dimension(profileInstallLocation.getPreferredSize().height, profileInstallLocation.getPreferredSize().height));
		selectProfileFolderButton.addActionListener(e -> InstallerGui.selectInstallLocation(() -> profileInstallLocation.getText(), s -> profileInstallLocation.setText(s)));

		installLocation.setText(Utils.findDefaultInstallDir().toString());
		profileInstallLocation.setText(Utils.findDefaultProfileInstallDir().toString());
	}
}
