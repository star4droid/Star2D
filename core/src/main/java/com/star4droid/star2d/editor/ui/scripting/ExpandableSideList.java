package com.star4droid.star2d.editor.ui.scripting;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable expandable side list widget for VisualNode.
 * Each section has a header and expandable child items.
 */
public class ExpandableSideList extends VisTable {

    private final List<Section> sections = new ArrayList<>();

    public ExpandableSideList() {
        top().left();
        defaults().expandX().fillX();
    }

    /** Adds a new expandable section */
    public Section addSection(String title) {
        Section section = new Section(title);
        sections.add(section);
        add(section.getRoot()).expandX().fillX().row();
        return section;
    }

    public static class Section {
        private final VisTextButton headerButton;
        private final VisTable contentTable;
        private final CollapsibleWidget collapsible;
        private final VisTable root;
        private boolean expanded = false;

        public Section(String title) {
            root = new VisTable(true);

            // Header button (clickable to toggle expand/collapse)
            headerButton = new VisTextButton(title);
            headerButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    toggle();
                }
            });

            contentTable = new VisTable(true);
            collapsible = new CollapsibleWidget(contentTable);
            collapsible.setCollapsed(true, true);

            root.add(headerButton).expandX().fillX().row();
            root.add(collapsible).expandX().fillX().row();
        }

        /** Toggle expand/collapse */
        public void toggle() {
            expanded = !expanded;
            collapsible.setCollapsed(!expanded, true);
        }

        /** Add a child item (button with click listener) */
        public void addItem(String label, Runnable onClick) {
            VisTextButton itemBtn = new VisTextButton(label);
            itemBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (onClick != null) onClick.run();
                }
            });
            contentTable.add(itemBtn).expandX().fillX().padLeft(15).row();
        }

        /** Add a custom actor instead of a button */
        public void addCustomItem(VisLabel label) {
            contentTable.add(label).expandX().fillX().padLeft(15).row();
        }

        public VisTable getRoot() {
            return root;
        }
    }
}