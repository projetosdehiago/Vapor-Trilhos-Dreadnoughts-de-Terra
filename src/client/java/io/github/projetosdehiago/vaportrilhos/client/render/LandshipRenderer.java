package io.github.projetosdehiago.vaportrilhos.client.render;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.module.LandshipModules;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Renderiza o landship com o modelo GeckoLib de {@code design/model/}. Aqui entram os detalhes
 * que dependem do estado do veículo: ponteiro do manômetro, leme, inclinação no terreno,
 * brilho da fornalha e quais módulos aparecem.
 */
public class LandshipRenderer extends GeoEntityRenderer<LandshipEntity, EntityRenderState> {
	private static final DataTicket<Float> PRESSURE = DataTicket.create("vapor_trilhos_pressure", Float.class);
	private static final DataTicket<Float> TURN = DataTicket.create("vapor_trilhos_turn", Float.class);
	private static final DataTicket<Float> PITCH = DataTicket.create("vapor_trilhos_pitch", Float.class);
	private static final DataTicket<Float> ROLL = DataTicket.create("vapor_trilhos_roll", Float.class);
	private static final DataTicket<Boolean> FIRE = DataTicket.create("vapor_trilhos_fire", Boolean.class);
	private static final DataTicket<Integer> MODULES = DataTicket.create("vapor_trilhos_modules", Integer.class);

	/** Tipos com osso próprio em cada encaixe do deque ({@code slot_<encaixe>_<tipo>}). */
	private static final ModuleType[] DECK_KINDS = {ModuleType.BED, ModuleType.CARGO, ModuleType.FURNACE};

	public LandshipRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<>(VaporTrilhos.id("landship")));
		this.shadowRadius = 1.6f;
		withRenderLayer(new AutoGlowingGeoLayer<>(this) {
			@Override
			protected @Nullable RenderType getRenderType(EntityRenderState renderState) {
				// a grade da fornalha e a lanterna só brilham com o fogo aceso
				return Boolean.TRUE.equals(geo(renderState).getGeckolibData(FIRE)) ? super.getRenderType(renderState) : null;
			}
		});
	}

	private static GeoRenderState geo(EntityRenderState state) {
		return (GeoRenderState) state;
	}

	@Override
	public void addRenderData(LandshipEntity landship, @Nullable Void relatedObject, EntityRenderState renderState, float partialTick) {
		GeoRenderState state = geo(renderState);
		state.addGeckolibData(PRESSURE, landship.getPressure());
		state.addGeckolibData(TURN, landship.getVisualTurnRate());
		state.addGeckolibData(PITCH, landship.getBodyPitch());
		state.addGeckolibData(ROLL, landship.getBodyRoll());
		state.addGeckolibData(FIRE, landship.isFireLit());
		state.addGeckolibData(MODULES, landship.getModuleBits());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
		GeoRenderState state = geo(renderPassInfo.renderState());

		// cada encaixe mostra só o módulo instalado nele
		Integer bits = state.getGeckolibData(MODULES);
		ModuleType[] installed = LandshipModules.decode(bits == null ? 0 : bits);
		for (ModuleSlot slot : ModuleSlot.DECK) {
			for (ModuleType kind : DECK_KINDS) {
				boolean hidden = installed[slot.ordinal()] != kind;
				snapshots.get("slot_" + slot.code + "_" + kind.id()).ifPresent(bone -> bone.skipRender(hidden).skipChildrenRender(hidden));
			}
		}
		boolean noCompactor = installed[ModuleSlot.FRONT.ordinal()] != ModuleType.COMPACTOR;
		snapshots.get("slot_front_compactor").ifPresent(bone -> bone.skipRender(noCompactor).skipChildrenRender(noCompactor));

		float pressure = orZero(state.getGeckolibData(PRESSURE));
		float needle = (0.75f - Math.min(pressure, BalanceConstants.MAX_BAR) / BalanceConstants.MAX_BAR * 1.5f) * Mth.PI;
		snapshots.get("gauge_needle").ifPresent(bone -> bone.setRotZ(needle));

		float turn = orZero(state.getGeckolibData(TURN));
		snapshots.get("helm").ifPresent(bone -> bone.setRotZ(Mth.clamp(-turn * 40f, -Mth.HALF_PI, Mth.HALF_PI)));

		float pitch = orZero(state.getGeckolibData(PITCH));
		float roll = orZero(state.getGeckolibData(ROLL));
		snapshots.get("body").ifPresent(bone -> bone.setRotX(pitch).setRotZ(roll));
	}

	private static float orZero(@Nullable Float value) {
		return value == null ? 0f : value;
	}
}
