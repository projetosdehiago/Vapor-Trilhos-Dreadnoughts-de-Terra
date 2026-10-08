package io.github.projetosdehiago.vaportrilhos.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class ModDataComponents {
	private ModDataComponents() {
	}

	/** Estado guardado no item quando o landship é recolhido com a chave. */
	public record LandshipData(float integrity, int waterMb) {
		public static final LandshipData NEW = new LandshipData(BalanceConstants.MAX_INTEGRITY, 0);

		public static final Codec<LandshipData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.fieldOf("integrity").forGetter(LandshipData::integrity),
				Codec.INT.optionalFieldOf("water_mb", 0).forGetter(LandshipData::waterMb)
		).apply(i, LandshipData::new));

		public static final StreamCodec<ByteBuf, LandshipData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, LandshipData::integrity,
				ByteBufCodecs.VAR_INT, LandshipData::waterMb,
				LandshipData::new);
	}

	public static final DataComponentType<LandshipData> LANDSHIP_DATA = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			VaporTrilhos.id("landship"),
			DataComponentType.<LandshipData>builder()
					.persistent(LandshipData.CODEC)
					.networkSynchronized(LandshipData.STREAM_CODEC)
					.build());

	public static void init() {
	}
}
