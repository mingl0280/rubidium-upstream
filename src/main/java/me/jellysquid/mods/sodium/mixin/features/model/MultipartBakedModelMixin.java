package me.jellysquid.mods.sodium.mixin.features.model;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraftforge.client.model.data.ModelData;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.*;

import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Predicate;

@Mixin(MultipartBakedModel.class)
public class MultipartBakedModelMixin {
    @Unique
    private final Map<BlockState, BakedModel[]> stateCacheFast = new Reference2ReferenceOpenHashMap<>();
    @Unique
    private final StampedLock lock = new StampedLock();

    @Shadow
    @Final
    private List<Pair<Predicate<BlockState>, BakedModel>> components;

    /**
     * @author JellySquid
     * @reason Avoid expensive allocations and replace bitfield indirection
     */
    @Overwrite
    public List<BakedQuad> getQuads(BlockState state, Direction face, Random random, ModelData modelData, RenderLayer renderLayer) {
        if (state == null) {
            return Collections.emptyList();
        }

        BakedModel[] models;

        long readStamp = this.lock.readLock();
        try {
            models = this.stateCacheFast.get(state);
        } finally {
            this.lock.unlockRead(readStamp);
        }

        if (models == null) {
            long writeStamp = this.lock.writeLock();
            try {
                List<BakedModel> modelList = new ArrayList<>(this.components.size());

                for (Pair<Predicate<BlockState>, BakedModel> pair : this.components) {
                    if (pair.getLeft().test(state)) {
                        modelList.add(pair.getRight());
                    }
                }

                models = modelList.toArray(BakedModel[]::new);
                this.stateCacheFast.put(state, models);
            } finally {
                this.lock.unlockWrite(writeStamp);
            }
        }

        List<BakedQuad> quads = new ArrayList<>();
        long seed = random.nextLong();

        for (BakedModel model : models) {
            random.setSeed(seed);

            if (renderLayer == null || model.getRenderTypes(state, random, modelData).contains(renderLayer)) // FORGE: Only put quad data if the model is using the render type passed
                quads.addAll(model.getQuads(state, face, random, modelData, renderLayer));
        }

        return quads;
    }
}
