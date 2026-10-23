<?php
/** The four pages and how they are built from the AlcorCut widgets. */
if (!defined('ABSPATH')) exit;

require_once ACS_DIR . 'includes/content.php';

/** Each page: title, slug and its widgets in order (widget name => settings; empty settings = the widget's defaults). */
function acs_page_defs() {
    $c = acs_content();
    $pro = function ($k) use ($c) { return ['acs-pro', $c['pro'][$k]]; };
    $tail = [['acs-beta', []], ['acs-share', []], ['acs-footer', []]];
    $feat = function ($k) use ($tail) {
        return array_merge([['acs-nav', ['solid' => 'yes']], ['acs-features', ['list' => $k]]], $tail);
    };
    return [
        'home' => ['title' => 'AlcorCut Pro', 'slug' => 'alcorcut-pro', 'widgets' => array_merge([
            ['acs-nav', []], ['acs-hero', []], ['acs-stats', []], ['acs-android', []], ['acs-video', []], ['acs-trio', []],
            $pro('capture'), $pro('photo'), $pro('video'), ['acs-gallery', []], ['acs-cards', []],
        ], $tail)],
        'capture' => ['title' => 'Capture — all features', 'slug' => 'capture-features', 'widgets' => $feat('capture')],
        'photo'   => ['title' => 'Photo editor — all features', 'slug' => 'photo-editor-features', 'widgets' => $feat('photo')],
        'video'   => ['title' => 'Video editor — all features', 'slug' => 'video-editor-features', 'widgets' => $feat('video')],
    ];
}

function acs_eid() { return substr(md5(uniqid('', true) . wp_rand()), 0, 7); }

/** Elementor data for a page: one full-width container per widget, no padding or gaps. */
function acs_elementor_data($widgets) {
    $out = [];
    foreach ($widgets as $w) {
        list($type, $settings) = $w;
        // Repeaters need an _id per row.
        foreach ($settings as $k => $v) {
            if (is_array($v) && isset($v[0]) && is_array($v[0])) {
                foreach ($settings[$k] as $i => $row) $settings[$k][$i]['_id'] = acs_eid();
            }
        }
        $zero = ['unit' => 'px', 'top' => '0', 'right' => '0', 'bottom' => '0', 'left' => '0', 'isLinked' => true];
        $out[] = [
            'id' => acs_eid(), 'elType' => 'container', 'isInner' => false,
            'settings' => ['content_width' => 'full', 'padding' => $zero, 'margin' => $zero,
                'flex_gap' => ['column' => '0', 'row' => '0', 'isLinked' => true, 'unit' => 'px', 'size' => 0]],
            'elements' => [[
                'id' => acs_eid(), 'elType' => 'widget', 'widgetType' => $type, 'settings' => (object) $settings, 'elements' => [],
            ]],
        ];
    }
    return $out;
}

/**
 * Creates the pages (or only the missing ones). With $replace, existing pages get fresh content.
 * The home page becomes the site's front page if the site still shows the latest posts there.
 */
function acs_create_pages($replace) {
    $ids = (array) get_option('acs_pages', []);
    foreach (acs_page_defs() as $key => $def) {
        $id = $ids[$key] ?? 0;
        $exists = $id && get_post_status($id) && get_post_status($id) !== 'trash';
        if ($exists && !$replace) continue;
        if (!$exists) {
            $id = wp_insert_post(['post_type' => 'page', 'post_status' => 'publish', 'post_title' => $def['title'], 'post_name' => $def['slug'], 'post_content' => '']);
            if (is_wp_error($id) || !$id) continue;
            $ids[$key] = $id;
        }
        update_post_meta($id, '_elementor_edit_mode', 'builder');
        update_post_meta($id, '_elementor_template_type', 'wp-page');
        if (defined('ELEMENTOR_VERSION')) update_post_meta($id, '_elementor_version', ELEMENTOR_VERSION);
        update_post_meta($id, '_wp_page_template', 'elementor_canvas');
        update_post_meta($id, '_elementor_data', wp_slash(wp_json_encode(acs_elementor_data($def['widgets']))));
        update_post_meta($id, '_acs_page', $key);
        delete_post_meta($id, '_elementor_css');
    }
    update_option('acs_pages', $ids);
    if (get_option('show_on_front') === 'posts' && !empty($ids['home'])) {
        update_option('show_on_front', 'page');
        update_option('page_on_front', $ids['home']);
    }
    if (class_exists('\Elementor\Plugin')) {
        try { \Elementor\Plugin::$instance->files_manager->clear_cache(); } catch (\Throwable $e) {}
    }
}
