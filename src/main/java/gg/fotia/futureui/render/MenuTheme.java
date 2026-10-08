package gg.fotia.futureui.render;

import gg.fotia.futureui.config.Node;

/** 按菜单选择主题；未声明主题的菜单继续使用原有资源名。 */
record MenuTheme(String id, Node configuration) {
    static MenuTheme of(Node assets, Node layout) {
        String id = layout.text("theme", "");
        if (!id.isEmpty() && !assets.child("themes").has(id)) throw new IllegalArgumentException("Unknown menu theme: " + id);
        return new MenuTheme(id, id.isEmpty() ? assets : assets.child("themes").child(id));
    }

    String skin(String name) {
        if (id.isEmpty() || name.isBlank()) return name;
        String local = configuration.child("skin-aliases").text(name, name);
        return configuration.child("canvas").child("styles").has(local) ? id + "_" + local : name;
    }

    String frame() { return id.isEmpty() ? "" : id + "/"; }
    String background() { return id.isEmpty() ? "" : "/" + id; }
}
