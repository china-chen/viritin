package org.vaadin.maddon.layouts;

import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;

public class MGridLayout extends GridLayout {

    public MGridLayout() {
        super.setSpacing(true);
        super.setMargin(true);
    }
    
    public MGridLayout(Component... components) {
        this();
        addComponents(components);
    }
    
    public MGridLayout with(Component... components) {
        addComponents(components);
        return this;
    }
    
    public MGridLayout withSpacing(boolean spacing) {
        setSpacing(spacing);
        return this;
    }

    public MGridLayout withMargin(boolean marging) {
        setMargin(marging);
        return this;
    }
    
    public MGridLayout withWidth(String width) {
        setWidth(width);
        return this;
    }

    public MGridLayout withFullWidth() {
        setWidth("100%");
        return this;
    }
    
    public MGridLayout withHeight(String height) {
        setHeight(height);
        return this;
    }
    
    public MGridLayout withFullHeight() {
        setHeight("100%");
        return this;
    }

}
