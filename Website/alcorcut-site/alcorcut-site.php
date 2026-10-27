<?php
/**
 * Plugin Name: AlcorCut Pro Website
 * Description: The AlcorCut Pro website for Elementor: a showcase home page and full feature lists for Capture, the Photo editor and the Video editor, with the app's own look, glowing pointer, scroll-to-top, share row, beta section and Vimeo player.
 * Version: 1.0.1
 * Requires at least: 6.0
 * Requires PHP: 7.4
 * Requires Plugins: elementor
 * Author: AlcorCut Pro
 * License: GPL-2.0-or-later
 * Text Domain: alcorcut-site
 */

if (!defined('ABSPATH')) exit;

define('ACS_VERSION', '1.0.1');
define('ACS_FILE', __FILE__);
define('ACS_DIR', plugin_dir_path(__FILE__));
define('ACS_URL', plugin_dir_url(__FILE__));

require_once ACS_DIR . 'includes/settings.php';
require_once ACS_DIR . 'includes/pages.php';

/* ------------------------------------------------------------------ helpers */

/** Plugin settings (Settings → AlcorCut Pro website), with defaults. */
function acs_opt($key = null) {
    $defaults = [
        'facebook'   => '',
        'youtube'    => '',
        'instagram'  => '',
        'tiktok'     => '',
        'beta_url'   => '',   // empty = the Facebook link
        'play_url'   => '',   // empty = "Coming soon to Google Play"
        'holder'     => 'AlcorCut Pro',
        'pointer'    => '1',
    ];
    $o = wp_parse_args((array) get_option('acs_settings', []), $defaults);
    return $key === null ? $o : ($o[$key] ?? '');
}

/** Link of one of the plugin's pages ('home', 'capture', 'photo', 'video'). */
function acs_page_url($key) {
    $ids = (array) get_option('acs_pages', []);
    if (!empty($ids[$key]) && get_post_status($ids[$key])) return get_permalink($ids[$key]);
    return $key === 'home' ? home_url('/') : '#';
}

/** Inline SVG for a social / share icon (Simple Icons, CC0) or one of our own. */
function acs_icon($name) {
    static $icons = null;
    if ($icons === null) $icons = json_decode((string) file_get_contents(ACS_DIR . 'data/icons.json'), true) ?: [];
    $own = [
        'linkedin' => 'M4 3a2 2 0 1 1 0 4 2 2 0 0 1 0-4zM2.3 8.6h3.4V21H2.3zM9 8.6h3.2v1.7h.1c.5-.9 1.6-1.9 3.4-1.9 3.6 0 4.3 2.4 4.3 5.4V21h-3.4v-6.2c0-1.5 0-3.4-2-3.4s-2.3 1.6-2.3 3.3V21H9z',
        'mail'     => 'M2 5h20v14H2zm2 2v.3l8 5.2 8-5.2V7zm16 2.7-8 5.2-8-5.2V17h16z',
        'link'     => 'M10.6 13.4a1 1 0 0 1 0-1.4l3.5-3.5a1 1 0 1 1 1.4 1.4L12 13.4a1 1 0 0 1-1.4 0zM8 21a5 5 0 0 1-3.5-8.5l2.1-2.1a1 1 0 1 1 1.4 1.4l-2.1 2.1a3 3 0 0 0 4.2 4.2l2.1-2.1a1 1 0 1 1 1.4 1.4l-2.1 2.1A5 5 0 0 1 8 21zm9.4-7.6a1 1 0 0 1-.7-1.7l2.1-2.1a3 3 0 0 0-4.2-4.2l-2.1 2.1a1 1 0 1 1-1.4-1.4l2.1-2.1a5 5 0 0 1 7.1 7.1l-2.1 2.1a1 1 0 0 1-.8.2z',
        'share'    => 'M18 16a3 3 0 0 0-2.4 1.2l-6.8-3.4a3 3 0 0 0 0-1.6l6.8-3.4A3 3 0 1 0 15 7l-6.8 3.4a3 3 0 1 0 0 3.2L15 17a3 3 0 1 0 3-1z',
    ];
    $d = $own[$name] ?? ($icons[$name] ?? '');
    return '<svg class="acs-ico" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="' . esc_attr($d) . '"/></svg>';
}

/* ------------------------------------------------------------------ copyright year */

/** [alcorcut_year] → the current year, worked out on every page load. */
add_shortcode('alcorcut_year', function () { return esc_html(wp_date('Y')); });

/* ------------------------------------------------------------------ assets */

add_action('wp_enqueue_scripts', function () {
    wp_register_style('acs-site', ACS_URL . 'assets/css/site.css', [], ACS_VERSION);
    wp_register_script('acs-site', ACS_URL . 'assets/js/site.js', [], ACS_VERSION, true);
    wp_localize_script('acs-site', 'ACS', [
        'pointer' => acs_opt('pointer') === '1',
        'copied'  => __('Link copied', 'alcorcut-site'),
    ]);
    if (acs_is_site_page()) { wp_enqueue_style('acs-site'); wp_enqueue_script('acs-site'); }
});

/** True on the plugin's own pages (they get the dark look, the pointer and the scroll-to-top button). */
function acs_is_site_page() {
    if (!is_singular()) return false;
    return (bool) get_post_meta(get_queried_object_id(), '_acs_page', true);
}

add_filter('body_class', function ($c) { if (acs_is_site_page()) $c[] = 'acs-page'; return $c; });

/** Scroll-to-top button on every plugin page (the pointer is drawn by site.js). */
add_action('wp_footer', function () {
    if (!acs_is_site_page()) return;
    echo '<button class="acs-top" type="button" aria-label="' . esc_attr__('Scroll to the top', 'alcorcut-site') . '">'
        . '<span class="acs-top__halo"></span><span class="acs-top__spark"></span>'
        . '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5l-7 7h4.5v7h5v-7H19z"/></svg></button>';
});

/* ------------------------------------------------------------------ Elementor */

add_action('elementor/elements/categories_registered', function ($em) {
    $em->add_category('alcorcut', ['title' => 'AlcorCut Pro', 'icon' => 'eicon-device-mobile']);
});

add_action('elementor/widgets/register', function ($wm) {
    require_once ACS_DIR . 'includes/widgets.php';
    foreach (acs_widget_classes() as $cls) $wm->register(new $cls());
});

/** Elementor's editor preview also needs the styles. */
add_action('elementor/preview/enqueue_styles', function () {
    wp_enqueue_style('acs-site', ACS_URL . 'assets/css/site.css', [], ACS_VERSION);
});

add_action('admin_notices', function () {
    if (did_action('elementor/loaded') || !current_user_can('activate_plugins')) return;
    echo '<div class="notice notice-warning"><p><strong>AlcorCut Pro website</strong> needs the free <strong>Elementor</strong> plugin. Install and activate Elementor, then open Settings → AlcorCut Pro website and press “Create the pages”.</p></div>';
});

register_activation_hook(__FILE__, function () {
    acs_create_pages(false);
});
