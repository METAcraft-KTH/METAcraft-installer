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

package net.fabricmc.installer.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mjson.Json;

public class InstallerData {
	public final String minecraftVersion;
	public final String fabricLoaderVersion;
	public final boolean createProfile;
	public final List<ModData> mods;

	public InstallerData(Json json) {
		this.minecraftVersion = json.at("minecraft_version").asString();
		this.fabricLoaderVersion = json.at("fabric_loader_version").asString();
		this.createProfile = json.at("create_profile").asBoolean();
		List<ModData> mods = new ArrayList<>();

		for (Json modJson : json.at("mods").asJsonList()) {
			mods.add(new ModData(modJson));
		}

		this.mods = Collections.unmodifiableList(mods);
	}

	public static InstallerData load() {
		try (InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream("installer.json")) {
			if (stream == null) {
				throw new RuntimeException("installer.json was not found");
			}

			String content = Utils.readString(stream);
			Json json = Json.read(content);
			return new InstallerData(json);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read bundled installer.json", e);
		}
	}

	public static class ModData {
		public final String modId;
		public final String name;
		public final String download;
		public final boolean enabled;
		public final boolean installByDefault;
		public final String descriptionEnglish;

		public ModData(Json json) {
			this.modId = json.at("modid").asString();
			this.name = json.at("name").asString();
			this.download = json.at("download").asString();
			this.enabled = json.at("enabled").asBoolean();
			this.installByDefault = json.at("install_by_default").asBoolean();
			this.descriptionEnglish = json.at("description_en").asString();
		}
	}
}
