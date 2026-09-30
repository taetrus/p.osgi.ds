package com.kk.pde2tycho.model;

/**
 * One entry of a launch's bundle list, e.g. {@code org.apache.felix.scr@2:true}.
 * {@code level}/{@code autoStart} are null where the launch says "default".
 * {@code workspace} is true for entries of selected_workspace_bundles.
 */
public record LaunchBundle(String id, Integer level, Boolean autoStart, boolean workspace) {
}
