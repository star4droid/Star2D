package com.star4droid.template;

import com.star4droid.template.Items.StageImp;

public interface SceneLoader {
    StageImp load(String sceneName, StageImp.StageLoaderParameters params) throws Exception;
}
