package de.f4ked.data_center_addon.industry.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Factory Manager shows ({@link FactoryManagerService}): the money of the viewer's factory over
 * the last 30 days, the machines around the manager and the trade per product and raw material.
 *
 * @param budgetCents     budget in the viewer's Purchase Terminals around the manager
 * @param purchaseTerminals Purchase Terminals of the viewer around the manager
 * @param salesTerminals  Sales Terminals of the viewer around the manager
 */
public record FactorySnapshot(long budgetCents, int purchaseTerminals, int salesTerminals, long spent30, long revenue30,
                              long spentToday, long revenueToday, List<Machine> machines, List<Sale> sales,
                              List<Purchase> purchases) {
    public static final FactorySnapshot EMPTY = new FactorySnapshot(0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());

    public long margin30() {
        return revenue30 - spent30;
    }

    /**
     * A factory machine around the manager.
     *
     * @param block  the machine's block id
     * @param state  {@link FactoryMachine.State} ordinal
     * @param output what it makes now (empty id path: nothing)
     * @param energy power stored, 0..1
     */
    public record Machine(BlockPos pos, ResourceLocation block, int state, ResourceLocation output, float energy) {
        public static final StreamCodec<FriendlyByteBuf, Machine> STREAM_CODEC = StreamCodec.of(
                (buf, m) -> {
                    buf.writeBlockPos(m.pos);
                    buf.writeResourceLocation(m.block);
                    buf.writeVarInt(m.state);
                    buf.writeResourceLocation(m.output);
                    buf.writeFloat(m.energy);
                },
                buf -> new Machine(buf.readBlockPos(), buf.readResourceLocation(), buf.readVarInt(), buf.readResourceLocation(),
                        buf.readFloat()));
    }

    /**
     * Sales of one product over the last 30 days, and its price now.
     *
     * @param product {@code Product} ordinal
     * @param demand  current demand in per mille
     */
    public record Sale(int product, long units, long cents, long priceNow, int demand) {
        public static final StreamCodec<FriendlyByteBuf, Sale> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.product);
                    buf.writeVarLong(s.units);
                    buf.writeVarLong(s.cents);
                    buf.writeVarLong(s.priceNow);
                    buf.writeVarInt(s.demand);
                },
                buf -> new Sale(buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt()));
    }

    /**
     * Purchases of one raw material over the last 30 days, and its price now.
     *
     * @param commodity {@code Commodity} ordinal
     */
    public record Purchase(int commodity, long units, long cents, long priceNow) {
        public static final StreamCodec<FriendlyByteBuf, Purchase> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.commodity);
                    buf.writeVarLong(p.units);
                    buf.writeVarLong(p.cents);
                    buf.writeVarLong(p.priceNow);
                },
                buf -> new Purchase(buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
    }

    public static final StreamCodec<FriendlyByteBuf, FactorySnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeVarLong(s.budgetCents);
                buf.writeVarInt(s.purchaseTerminals);
                buf.writeVarInt(s.salesTerminals);
                buf.writeVarLong(s.spent30);
                buf.writeVarLong(s.revenue30);
                buf.writeVarLong(s.spentToday);
                buf.writeVarLong(s.revenueToday);
                writeList(buf, s.machines, Machine.STREAM_CODEC);
                writeList(buf, s.sales, Sale.STREAM_CODEC);
                writeList(buf, s.purchases, Purchase.STREAM_CODEC);
            },
            buf -> new FactorySnapshot(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarLong(), readList(buf, Machine.STREAM_CODEC, 512), readList(buf, Sale.STREAM_CODEC, 64),
                    readList(buf, Purchase.STREAM_CODEC, 64)));

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, StreamCodec<FriendlyByteBuf, T> codec) {
        buf.writeVarInt(list.size());
        for (T e : list) {
            codec.encode(buf, e);
        }
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, StreamCodec<FriendlyByteBuf, T> codec, int max) {
        int size = Math.min(buf.readVarInt(), max);
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(codec.decode(buf));
        }
        return List.copyOf(list);
    }
}
