package eu.smashmc.stonedmc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import eu.smashmc.api.core.Inject;
import eu.smashmc.api.core.Invoke;
import eu.smashmc.api.core.Managed;
import eu.smashmc.api.core.packet.PacketEvent;
import eu.smashmc.api.core.packet.PacketUtil;
import eu.smashmc.lib.hybrid.cloud.CloudWrapper;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

@Managed
public class PacketTransformer implements Listener {

	private static final Item STONE_ITEM = Item.getItemOf(Blocks.STONE);

	private static final String SKIN_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTcxMTkxMDA3MDA2NCwKICAicHJvZmlsZUlkIiA6ICJiMDc3MDc2ZTU0MzM0ODA3ODdjNjQwMjgzZDIwNTVkZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBTEVYNDMxMDAiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmE4N2YyZjBjZTVkMzQ5OWM2MTExZDViY2NkYTY5ZGNhZjA3Mjc4YzdmMTk0OTlmZGU2OTFiYmU3NjZkODNjNSIKICAgIH0KICB9Cn0=";
	private static final String SKIN_SIGNATURE = "mPqIDRZ7nTRXSGfD88mFbCrJU/ZNAcck6wKlPjqdN4uc9l2h/acXBJruhIrT7gcoYRZNDW54L16hGg0T3540ztTVcGuvF+bz8thulPvsQk+T78MjJNSi/GqNYogJ/ravZmYfcF+DaRxzm/7cD1XCosyWhrL4IMfM1Jk53nBLWEqipPiQrlrsZHFwoHYAC/9EvOJfQcSSKu9mniKWqHytY/cCWu4cy+NGY3MVTKfIeoDzlT/i7HYI+bl/lGA441vSuOCL1zhnUyEP2xAqEwcb7vVhL/8RR6GQOmA0Cq6p3M6tjTDRRSqML22GrAXkog9SmKD7vhdPOdXbue6hqFdTqH7JzZH0FGnoMCv3bCQuMZ95Oe6mkIlLQg0/55Lui/MmtzMsPOdDYw6hf6dij0lmWURKzdGKwU8mSeZvdD4bRSWvKZvzBlc6ZWAh3jUpCqLrGmeOPbWCwi9mYLYAPbLKIjAcSERomq2viG1siiePmlHpxNUn+bl0SVPLCjiIUxz89aGx6iZCLhyKc3x0ai/yTY3L57wlP9Hi53CmXWo4Qs9hjUk0fdnL1BCBTTdh3IC78sbNIevGejN1ZzNSEkJfyd0wF/46/Tk5jhlhKMb449bg0x5UlZpcnL5mPRMdtpNUlceS9RqhTv+73FpR/TehdiLUSLTo6GVB0SLB8tq/UjE=";

	public static final GameProfile STONE_PROFILE = new GameProfile(UUID.randomUUID(), "stoned");

	private Field windowItemsField;
	private Field slotItemField;
	private Field metadataField;
	private Field equipmentStack;

	private Field multiBlockField;

	private Field multiBlockBlockField;

	private Field playerInfoDataProfileField;

	@Inject
	private Plugin plugin;

	@Invoke
	public void init() throws NoSuchFieldException {
		prepareReflections();
		PacketTransformer.transformProfile(STONE_PROFILE);
		PacketUtil.listenPacket(PacketPlayOutWindowItems.class, this::transformWindowItems);
		PacketUtil.listenPacket(PacketPlayOutSetSlot.class, this::transformSetSlot);
		PacketUtil.listenPacket(PacketPlayOutEntityMetadata.class, this::transformEntityMetadata);
		PacketUtil.listenPacket(PacketPlayOutEntityEquipment.class, this::transformEntityEquipment);
		PacketUtil.listenPacket(PacketPlayOutPlayerInfo.class, this::transformPlayerInfo);
		// Disabled ingame as it breaks some mechanics
		if (CloudWrapper.isLobbyServer()) {
			PacketUtil.listenPacket(PacketPlayOutBlockChange.class, this::transformBlockChange);
			PacketUtil.listenPacket(PacketPlayOutMultiBlockChange.class, this::transformMultiBlockChange);
		}
		PacketUtil.listenPacket(PacketPlayInBlockPlace.class, this::handleBlockPlace);
	}

	private void prepareReflections() throws NoSuchFieldException {
		windowItemsField = PacketPlayOutWindowItems.class.getDeclaredField("b");
		windowItemsField.setAccessible(true);

		slotItemField = PacketPlayOutSetSlot.class.getDeclaredField("c");
		slotItemField.setAccessible(true);

		metadataField = PacketPlayOutEntityMetadata.class.getDeclaredField("b");
		metadataField.setAccessible(true);

		equipmentStack = PacketPlayOutEntityEquipment.class.getDeclaredField("c");
		equipmentStack.setAccessible(true);

		multiBlockField = PacketPlayOutMultiBlockChange.class.getDeclaredField("b");
		multiBlockField.setAccessible(true);

		multiBlockBlockField = PacketPlayOutMultiBlockChange.MultiBlockChangeInfo.class.getDeclaredField("c");
		multiBlockBlockField.setAccessible(true);

		playerInfoDataProfileField = PacketPlayOutPlayerInfo.PlayerInfoData.class.getDeclaredField("d");
		playerInfoDataProfileField.setAccessible(true);
	}

	private void handleBlockPlace(PacketEvent<PacketPlayInBlockPlace> packetPlayInBlockPlacePacketEvent) {
		Bukkit.getScheduler().runTask(plugin, () -> packetPlayInBlockPlacePacketEvent.getPlayer().updateInventory());
	}

	private void transformBlockChange(PacketEvent<PacketPlayOutBlockChange> packetPlayOutBlockChangePacketEvent) {
		try {
			var packet = packetPlayOutBlockChangePacketEvent.getPacket();
			if (packet.block.getBlock().getMaterial().isSolid()) {
				packet.block = Blocks.STONE.getBlockData();
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void transformMultiBlockChange(PacketEvent<PacketPlayOutMultiBlockChange> packetPlayOutBlockChangePacketEvent) {
		try {
			var packet = packetPlayOutBlockChangePacketEvent.getPacket();
			PacketPlayOutMultiBlockChange.MultiBlockChangeInfo[] b = (PacketPlayOutMultiBlockChange.MultiBlockChangeInfo[]) multiBlockField.get(packet);
			for (var info : b) {
				multiBlockBlockField.set(info, Blocks.STONE.getBlockData());
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void transformPlayerInfo(PacketEvent<PacketPlayOutPlayerInfo> packetPlayOutPlayerInfoPacketEvent) {
		var packet = packetPlayOutPlayerInfoPacketEvent.getPacket();
		for (var info : packet.getPlayersInfo()) {
			try {
				var originalProfile = info.a();
				var stonedProfile = new GameProfile(originalProfile.getId(), originalProfile.getName());
				transformProfile(stonedProfile);
				playerInfoDataProfileField.set(info, stonedProfile);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void transformEntityEquipment(PacketEvent<PacketPlayOutEntityEquipment> packetPlayOutEntityEquipmentPacketEvent) {
		try {
			var packet = packetPlayOutEntityEquipmentPacketEvent.getPacket();
			var itemStack = (ItemStack) equipmentStack.get(packet);
			if (itemStack != null) {
				itemStack.setItem(STONE_ITEM);
				itemStack.setData(0);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void transformEntityMetadata(PacketEvent<PacketPlayOutEntityMetadata> packetPlayOutEntityMetadataPacketEvent) {
		try {
			var packet = packetPlayOutEntityMetadataPacketEvent.getPacket();
			List<DataWatcher.WatchableObject> objects = (List<DataWatcher.WatchableObject>) metadataField.get(packet);

			if (objects != null) {
				for (DataWatcher.WatchableObject obj : objects) {
					if (obj.c() == 5) {
						ItemStack stack = (ItemStack) obj.b();
						stack.setItem(STONE_ITEM);
						stack.setData(0);
					}
				}
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void transformSetSlot(PacketEvent<PacketPlayOutSetSlot> packetPlayOutSetSlotPacketEvent) {
		try {
			ItemStack itemStack = (ItemStack) slotItemField.get(packetPlayOutSetSlotPacketEvent.getPacket());
			this.transformItem(itemStack);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void transformWindowItems(PacketEvent<PacketPlayOutWindowItems> packetPlayOutWindowItemsPacketEvent) {
		try {
			var packet = packetPlayOutWindowItemsPacketEvent.getPacket();
			ItemStack[] itemStacks = (ItemStack[]) windowItemsField.get(packet);
			for (ItemStack itemStack : itemStacks) {
				this.transformItem(itemStack);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}


	private void transformItem(ItemStack itemStack) {
		if (itemStack == null)
			return;

		itemStack.setItem(STONE_ITEM);
		itemStack.setData(0);
	}

	public static void transformProfile(GameProfile profile) {
		var textures = new Property("textures", SKIN_VALUE, SKIN_SIGNATURE);
		profile.getProperties().removeAll("textures");
		profile.getProperties().put("textures", textures);
	}
}
