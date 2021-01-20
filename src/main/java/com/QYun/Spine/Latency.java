package com.QYun.Spine;

import com.QYun.SuperSpineViewer.GUI.Controller;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.SpineLatency.*;
import com.esotericsoftware.SpineLatency.AnimationState.TrackEntry;
import javafx.application.Platform;

public class Latency extends SuperSpine {

    private PolygonSpriteBatch batch;
    private SkeletonMeshRenderer renderer;

    private OrthographicCamera camera;
    private Skeleton skeleton;
    private AnimationState state;

    private void lists(Array<Skin> skins, Array<Animation> animations) {
        for (Skin skin : skins)
            skinsList.add(skin.getName());

        for (Animation animation : animations)
            animatesList.add(animation.getName());
    }

    private boolean loadSkel() {
        TextureAtlasData atlasData;
        atlasData = new TextureAtlasData(atlasFile, atlasFile.parent(), false);

        TextureAtlas atlas = new TextureAtlas(atlasData) {
            public AtlasRegion findRegion(String name) {
                AtlasRegion region = super.findRegion(name);
                if (region == null) {
                    FileHandle file = skelFile.sibling(name + ".png");
                    if (file.exists()) {
                        Texture texture = new Texture(file);
                        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
                        region = new AtlasRegion(texture, 0, 0, texture.getWidth(), texture.getHeight());
                        region.name = name;
                    }
                }
                return region;
            }
        };

        SkeletonData skeletonData;
        if (isBinary) {
            SkeletonBinary binary = new SkeletonBinary(atlas);
            binary.setScale(scale.get());
            skeletonData = binary.readSkeletonData(skelFile);
        } else {
            SkeletonJson json = new SkeletonJson(atlas);
            json.setScale(scale.get());
            skeletonData = json.readSkeletonData(skelFile);
        }
        if (skeletonData.getBones().size == 0) {
            System.out.println("骨骼为空");
            return false;
        }

        skeleton = new Skeleton(skeletonData);
        skeleton.updateWorldTransform();
        skeleton.setToSetupPose();
        skeleton.setPosition(X.get(), Y.get());

        state = new AnimationState(new AnimationStateData(skeletonData));
        spineVersion.set(skeletonData.getVersion());
        projectName.set(skeletonData.getName());

        if (skinsList.isEmpty())
            lists(skeletonData.getSkins(), skeletonData.getAnimations());

        return true;
    }

    private void listeners() {
        skin.addListener((observable, oldValue, newValue) -> {
            if (skeleton != null) {
                if (newValue == null)
                    skeleton.setSkin((Skin) null);
                else skeleton.setSkin(newValue);
                skeleton.setSlotsToSetupPose();
            }
        });

        animate.addListener((observable, oldValue, newValue) -> {
            if (state != null) {
                if (newValue != null) {
                    state.setAnimation(0, newValue, isLoop.get());
                    isPlay.set(true);
                } else {
                    isPlay.set(false);
                }
            }
        });

        isLoop.addListener((observable, oldValue, newValue) -> {
            if (state != null) {
                if (animate.get() == null) {
                    isPlay.set(false);
                } else {
                    state.setAnimation(0, animate.get(), newValue);
                    if (newValue) isPlay.set(true);
                }
            }
        });

        isPlay.addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                if (state != null) {
                    if (newValue) {
                        if (animate.get() == null)
                            state.setAnimation(0, animatesList.get(0), isLoop.get());
                        else if (!isLoop.get())
                            state.setAnimation(0, animate.get(), isLoop.get());
                        state.setTimeScale(speed.get());
                    } else state.setTimeScale(0);
                }
            }
        });

        scale.addListener((observable, oldValue, newValue) -> {
            if (state != null) {
                Gdx.app.postRunnable(this::loadSkel);
                if (animate.get() != null) {
                    state.setAnimation(0, animate.get(), isLoop.get());
                    isPlay.set(true);
                }
            }
        });

        X.addListener((observable, oldValue, newValue) -> {
            if (state != null) {
                Gdx.app.postRunnable(this::loadSkel);
                if (animate.get() != null) {
                    state.setAnimation(0, animate.get(), isLoop.get());
                    isPlay.set(true);
                }
            }
        });

        Y.addListener((observable, oldValue, newValue) -> {
            if (state != null) {
                Gdx.app.postRunnable(this::loadSkel);
                if (animate.get() != null) {
                    state.setAnimation(0, animate.get(), isLoop.get());
                    isPlay.set(true);
                }
            }
        });

        speed.addListener((observable, oldValue, newValue) -> {
            if (state != null)
                state.setTimeScale(speed.get());
        });

        isReload.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                skinsList.clear();
                animatesList.clear();
                skin.set(null);
                animate.set(null);
                Gdx.app.postRunnable(this::loadSkel);
                isReload.set(false);
            }
        });
    }

    void create() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        batch = new PolygonSpriteBatch();
        camera = new OrthographicCamera(w, h);
        renderer = new SkeletonMeshRenderer();
        renderer.setPremultipliedAlpha(true);

        if (loadSkel())
            listeners();
    }

    void render() {
        state.update(Gdx.graphics.getDeltaTime());
        state.apply(skeleton);
        skeleton.updateWorldTransform();

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.graphics.setTitle("FPS : " + Gdx.graphics.getFramesPerSecond());
        renderer.setPremultipliedAlpha(Controller.preA);

        camera.update();
        batch.getProjectionMatrix().set(camera.combined);
        batch.begin();
        renderer.draw(batch, skeleton);
        batch.end();

        if (state != null) {
            TrackEntry entry = state.getCurrent(0);
            if (entry != null) {
                percent = entry.getTime() / entry.getEndTime();
                if (entry.getLoop())
                    percent %= 1;
                if (isPlay.get())
                    Platform.runLater(() -> Controller.progressBar.setProgress(percent));
                if (percent >= 1 && !isLoop.get())
                    isPlay.set(false);
            }
        }
    }

    void resize() {
        float x = camera.position.x;
        float y = camera.position.y;
        camera.setToOrtho(false);
        camera.position.set(x, y, 0);
    }
}