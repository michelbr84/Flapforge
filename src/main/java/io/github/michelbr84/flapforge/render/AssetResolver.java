package io.github.michelbr84.flapforge.render;

import java.util.Objects;
import java.util.Optional;

/**
 * The id lookup order every renderer uses (D18): a world may override an asset that otherwise
 * applies everywhere.
 *
 * <p>{@link #sprite(String, String)} tries {@code worlds/<world>/<id>} first, then the bare
 * {@code <id>}, and answers empty when the manifest declares neither — at which point the caller
 * draws {@link ProceduralArt}. With the shipped empty manifest that is always the outcome, which
 * is exactly what {@code ProceduralRenderTest} pins down.
 *
 * <p>The running game has exactly one resolver, installed by the application with {@link #use} and
 * read by the renderers through {@link #active()} — the same shape {@code content.Strings} and
 * {@code ui.UiCues} use, and the reason a renderer does not have to be handed one down a
 * constructor chain that would otherwise carry it through every screen. The default is
 * {@link #empty()}, so a test that installs nothing renders procedurally.
 */
public final class AssetResolver {

    /** Prefix of a world-specific override. */
    public static final String WORLD_PREFIX = "worlds/";

    private static volatile AssetResolver active = new AssetResolver(AssetManager.empty());

    private final AssetManager assets;

    /**
     * Creates a resolver over an asset manager.
     *
     * @param assets the manager
     */
    public AssetResolver(AssetManager assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    /**
     * A resolver over the shipped manifest.
     *
     * @return the resolver
     */
    public static AssetResolver fromClasspath() {
        return new AssetResolver(AssetManager.fromClasspath());
    }

    /**
     * A resolver that never finds anything (procedural art only).
     *
     * @return the resolver
     */
    public static AssetResolver empty() {
        return new AssetResolver(AssetManager.empty());
    }

    /**
     * The resolver the renderers use.
     *
     * @return the installed resolver, or an empty one when the application installed none
     */
    public static AssetResolver active() {
        return active;
    }

    /**
     * Installs the resolver the renderers use. The application calls it once at launch; a test
     * that installs one must put {@link #empty()} back afterwards.
     *
     * @param resolver the resolver, or {@code null} for an empty one
     */
    public static void use(AssetResolver resolver) {
        active = resolver != null ? resolver : new AssetResolver(AssetManager.empty());
    }

    /**
     * The manager behind the resolver.
     *
     * @return the manager
     */
    public AssetManager assets() {
        return assets;
    }

    /**
     * The id a world-specific override would carry.
     *
     * @param id the base id
     * @param worldId the world, may be {@code null}
     * @return {@code worlds/<world>/<id>}, or {@code id} when there is no world
     */
    public static String worldId(String id, String worldId) {
        return worldId == null || worldId.isBlank() ? id : WORLD_PREFIX + worldId + "/" + id;
    }

    /**
     * Resolves a sprite: world override, then the plain id, then empty.
     *
     * @param id the base id
     * @param worldId the world whose override wins, or {@code null}
     * @return the sprite, or empty when the caller must draw procedural art
     */
    public Optional<Sprite> sprite(String id, String worldId) {
        Optional<Sprite> scoped = assets.sprite(worldId(id, worldId));
        return scoped.isPresent() ? scoped : assets.sprite(id);
    }

    /**
     * Resolves a sheet: world override, then the plain id, then empty.
     *
     * @param id the base id
     * @param worldId the world whose override wins, or {@code null}
     * @return the sheet, or empty when the caller must draw procedural art
     */
    public Optional<SpriteSheet> sheet(String id, String worldId) {
        Optional<SpriteSheet> scoped = assets.sheet(worldId(id, worldId));
        return scoped.isPresent() ? scoped : assets.sheet(id);
    }

    @Override
    public String toString() {
        return "AssetResolver" + assets;
    }
}
