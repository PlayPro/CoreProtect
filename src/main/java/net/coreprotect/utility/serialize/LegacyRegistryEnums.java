package net.coreprotect.utility.serialize;

final class LegacyRegistryEnums {

    private LegacyRegistryEnums() {
    }

    enum ArtValue implements LegacyMetadataCodec.RegistryValue {
        ALBAN,
        AZTEC,
        AZTEC2,
        BACKYARD,
        BAROQUE,
        BOMB,
        BOUQUET,
        BURNING_SKULL,
        BUST,
        CAVEBIRD,
        CHANGING,
        COTAN,
        COURBET,
        CREEBET,
        DONKEY_KONG,
        EARTH,
        ENDBOSS,
        FERN,
        FIGHTERS,
        FINDING,
        FIRE,
        GRAHAM,
        HUMBLE,
        KEBAB,
        LOWMIST,
        MATCH,
        MEDITATIVE,
        ORB,
        OWLEMONS,
        PASSAGE,
        PIGSCENE,
        PLANT,
        POINTER,
        POND,
        POOL,
        PRAIRIE_RIDE,
        SEA,
        SKELETON,
        SKULL_AND_ROSES,
        STAGE,
        SUNFLOWERS,
        SUNSET,
        TIDES,
        UNPACKED,
        VOID,
        WANDERER,
        WASTELAND,
        WATER,
        WIND,
        WITHER;
    }

    enum FluidValue implements LegacyMetadataCodec.RegistryValue {
        EMPTY,
        FLOWING_LAVA,
        FLOWING_WATER,
        LAVA,
        WATER;
    }

    enum BiomeValue implements LegacyMetadataCodec.RegistryValue {
        BADLANDS,
        BADLANDS_PLATEAU,
        BAMBOO_JUNGLE,
        BAMBOO_JUNGLE_HILLS,
        BASALT_DELTAS,
        BEACH,
        BIRCH_FOREST,
        BIRCH_FOREST_HILLS,
        CHERRY_GROVE,
        COLD_OCEAN,
        CRIMSON_FOREST,
        CUSTOM,
        DARK_FOREST,
        DARK_FOREST_HILLS,
        DEEP_COLD_OCEAN,
        DEEP_DARK,
        DEEP_FROZEN_OCEAN,
        DEEP_LUKEWARM_OCEAN,
        DEEP_OCEAN,
        DEEP_WARM_OCEAN,
        DESERT,
        DESERT_HILLS,
        DESERT_LAKES,
        DRIPSTONE_CAVES,
        END_BARRENS,
        END_HIGHLANDS,
        END_MIDLANDS,
        ERODED_BADLANDS,
        FLOWER_FOREST,
        FOREST,
        FROZEN_OCEAN,
        FROZEN_PEAKS,
        FROZEN_RIVER,
        GIANT_SPRUCE_TAIGA("old_growth_spruce_taiga"),
        GIANT_SPRUCE_TAIGA_HILLS,
        GIANT_TREE_TAIGA("old_growth_pine_taiga"),
        GIANT_TREE_TAIGA_HILLS,
        GRAVELLY_MOUNTAINS("windswept_gravelly_hills"),
        GROVE,
        ICE_SPIKES,
        JAGGED_PEAKS,
        JUNGLE,
        JUNGLE_EDGE("sparse_jungle"),
        JUNGLE_HILLS,
        LUKEWARM_OCEAN,
        LUSH_CAVES,
        MANGROVE_SWAMP,
        MEADOW,
        MODIFIED_BADLANDS_PLATEAU,
        MODIFIED_GRAVELLY_MOUNTAINS,
        MODIFIED_JUNGLE,
        MODIFIED_JUNGLE_EDGE,
        MODIFIED_WOODED_BADLANDS_PLATEAU,
        MOUNTAINS("windswept_hills"),
        MOUNTAIN_EDGE,
        MUSHROOM_FIELDS,
        MUSHROOM_FIELD_SHORE,
        NETHER_WASTES,
        OCEAN,
        OLD_GROWTH_BIRCH_FOREST,
        OLD_GROWTH_PINE_TAIGA,
        OLD_GROWTH_SPRUCE_TAIGA,
        PLAINS,
        RIVER,
        SAVANNA,
        SAVANNA_PLATEAU,
        SHATTERED_SAVANNA,
        SHATTERED_SAVANNA_PLATEAU,
        SMALL_END_ISLANDS,
        SNOWY_BEACH,
        SNOWY_MOUNTAINS,
        SNOWY_PLAINS,
        SNOWY_SLOPES,
        SNOWY_TAIGA,
        SNOWY_TAIGA_HILLS,
        SNOWY_TAIGA_MOUNTAINS,
        SNOWY_TUNDRA("snowy_plains"),
        SOUL_SAND_VALLEY,
        SPARSE_JUNGLE,
        STONE_SHORE("stony_shore"),
        STONY_PEAKS,
        STONY_SHORE,
        SUNFLOWER_PLAINS,
        SWAMP,
        SWAMP_HILLS,
        TAIGA,
        TAIGA_HILLS,
        TAIGA_MOUNTAINS,
        TALL_BIRCH_FOREST("old_growth_birch_forest"),
        TALL_BIRCH_HILLS,
        THE_END,
        THE_VOID,
        WARM_OCEAN,
        WARPED_FOREST,
        WINDSWEPT_FOREST,
        WINDSWEPT_GRAVELLY_HILLS,
        WINDSWEPT_HILLS,
        WINDSWEPT_SAVANNA,
        WOODED_BADLANDS,
        WOODED_BADLANDS_PLATEAU("wooded_badlands"),
        WOODED_HILLS,
        WOODED_MOUNTAINS("windswept_forest");

        BiomeValue() {
            this(null);
        }

        private final String path;

        BiomeValue(String path) {
            this.path = path;
        }

        @Override
        public String key() {
            return path == null ? LegacyMetadataCodec.RegistryValue.super.key() : "minecraft:" + path;
        }
    }

    enum PatternValue implements LegacyMetadataCodec.RegistryValue {
        BASE,
        BORDER,
        BRICKS,
        CIRCLE,
        CIRCLE_MIDDLE("circle"),
        CREEPER,
        CROSS,
        CURLY_BORDER,
        DIAGONAL_LEFT,
        DIAGONAL_LEFT_MIRROR("diagonal_up_left"),
        DIAGONAL_RIGHT,
        DIAGONAL_RIGHT_MIRROR("diagonal_right"),
        DIAGONAL_UP_LEFT,
        DIAGONAL_UP_RIGHT,
        FLOW,
        FLOWER,
        GLOBE,
        GRADIENT,
        GRADIENT_UP,
        GUSTER,
        HALF_HORIZONTAL,
        HALF_HORIZONTAL_BOTTOM,
        HALF_HORIZONTAL_MIRROR("half_horizontal_bottom"),
        HALF_VERTICAL,
        HALF_VERTICAL_MIRROR("half_vertical_right"),
        HALF_VERTICAL_RIGHT,
        MOJANG,
        PIGLIN,
        RHOMBUS,
        RHOMBUS_MIDDLE("rhombus"),
        SKULL,
        SMALL_STRIPES,
        SQUARE_BOTTOM_LEFT,
        SQUARE_BOTTOM_RIGHT,
        SQUARE_TOP_LEFT,
        SQUARE_TOP_RIGHT,
        STRAIGHT_CROSS,
        STRIPE_BOTTOM,
        STRIPE_CENTER,
        STRIPE_DOWNLEFT,
        STRIPE_DOWNRIGHT,
        STRIPE_LEFT,
        STRIPE_MIDDLE,
        STRIPE_RIGHT,
        STRIPE_SMALL("small_stripes"),
        STRIPE_TOP,
        TRIANGLES_BOTTOM,
        TRIANGLES_TOP,
        TRIANGLE_BOTTOM,
        TRIANGLE_TOP;

        PatternValue() {
            this(null);
        }

        private final String path;

        PatternValue(String path) {
            this.path = path;
        }

        @Override
        public String key() {
            return path == null ? LegacyMetadataCodec.RegistryValue.super.key() : "minecraft:" + path;
        }
    }

    enum MapCursorValue implements LegacyMetadataCodec.RegistryValue {
        BANNER_BLACK,
        BANNER_BLUE,
        BANNER_BROWN,
        BANNER_CYAN,
        BANNER_GRAY,
        BANNER_GREEN,
        BANNER_LIGHT_BLUE,
        BANNER_LIGHT_GRAY,
        BANNER_LIME,
        BANNER_MAGENTA,
        BANNER_ORANGE,
        BANNER_PINK,
        BANNER_PURPLE,
        BANNER_RED,
        BANNER_WHITE,
        BANNER_YELLOW,
        BLUE_MARKER,
        BLUE_POINTER("blue_marker"),
        DESERT_VILLAGE,
        FRAME,
        GREEN_POINTER("frame"),
        JUNGLE_TEMPLE,
        MANSION,
        MONUMENT,
        PLAINS_VILLAGE,
        PLAYER,
        PLAYER_OFF_LIMITS,
        PLAYER_OFF_MAP,
        RED_MARKER,
        RED_POINTER("red_marker"),
        RED_X,
        SAVANNA_VILLAGE,
        SMALL_WHITE_CIRCLE("player_off_limits"),
        SNOWY_VILLAGE,
        SWAMP_HUT,
        TAIGA_VILLAGE,
        TARGET_POINT,
        TARGET_X,
        TEMPLE,
        TRIAL_CHAMBERS,
        VILLAGE_DESERT,
        VILLAGE_PLAINS,
        VILLAGE_SAVANNA,
        VILLAGE_SNOWY,
        VILLAGE_TAIGA,
        WHITE_CIRCLE("player_off_map"),
        WHITE_CROSS("target_x"),
        WHITE_POINTER("player");

        MapCursorValue() {
            this(null);
        }

        private final String path;

        MapCursorValue(String path) {
            this.path = path;
        }

        @Override
        public String key() {
            return path == null ? LegacyMetadataCodec.RegistryValue.super.key() : "minecraft:" + path;
        }
    }
}
