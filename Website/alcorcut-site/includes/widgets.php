<?php
/** The AlcorCut Elementor widgets: every text, picture and link is editable in Elementor's panel. */
if (!defined('ABSPATH')) exit;

use Elementor\Controls_Manager;
use Elementor\Repeater;
use Elementor\Widget_Base;

require_once ACS_DIR . 'includes/content.php';

function acs_widget_classes() {
    return ['ACS_Nav', 'ACS_Hero', 'ACS_Stats', 'ACS_Android', 'ACS_Video', 'ACS_Trio', 'ACS_Pro', 'ACS_Gallery', 'ACS_Cards', 'ACS_Beta', 'ACS_Share', 'ACS_Footer', 'ACS_Features'];
}

/* ------------------------------------------------------------------ small helpers */

/** Rich text that may hold <em>, <strong>, <p>, <br> and links. */
function acs_rich($s) { return wp_kses_post((string) $s); }

/** A line icon (own drawings). */
function acs_line_icon($k) {
    $p = [
        'capture' => '<rect x="3" y="7" width="18" height="13" rx="3"/><path d="M8 7l1.5-3h5L16 7"/><circle cx="12" cy="13.5" r="3.6"/>',
        'photo'   => '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="9.5" r="1.8"/><path d="M4 18l5.5-5.5 3.5 3.5 2.5-2.5L20 18"/>',
        'video'   => '<rect x="2.5" y="5" width="14" height="14" rx="3"/><path d="M16.5 10l5-3v10l-5-3"/><path d="M6 9h5"/>',
        'chip'    => '<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/><path d="M10 10h4v4h-4z"/>',
        'lock'    => '<rect x="4" y="10" width="16" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/><circle cx="12" cy="15.5" r="1.4"/>',
        'flow'    => '<circle cx="5" cy="6" r="2.5"/><circle cx="19" cy="6" r="2.5"/><circle cx="12" cy="18" r="2.5"/><path d="M7.5 6h9M6.3 8.2l4.4 7.6M17.7 8.2l-4.4 7.6"/>',
        'watch'   => '<rect x="6" y="6" width="12" height="12" rx="4"/><path d="M9 6l1-4h4l1 4M9 18l1 4h4l1-4"/><path d="M12 9.5V12l2 1.2"/>',
        'screens' => '<rect x="2" y="4" width="14" height="10" rx="2"/><rect x="15" y="9" width="7" height="12" rx="2"/><path d="M6 18h6M9 14v4"/>',
        'globe'   => '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.2 3 14.8 0 18M12 3c-3 3.2-3 14.8 0 18"/>',
        'arrow'   => '<path d="M5 12h14M13 6l6 6-6 6"/>',
        'play'    => '<path d="M8 5.5v13l11-6.5z"/>',
        'store'   => '<path d="M12 3v11M7.5 9.5L12 14l4.5-4.5"/><path d="M4 16v2.5A2.5 2.5 0 0 0 6.5 21h11a2.5 2.5 0 0 0 2.5-2.5V16"/>',
        'search'  => '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
        'menu'    => '<path d="M4 7h16M4 12h16M4 17h16"/>',
    ][$k] ?? '';
    return '<svg class="acs-li" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' . $p . '</svg>';
}

/** A phone or tablet drawn in CSS around a screenshot. */
function acs_device($img, $kind = 'phone', $cls = '', $alt = '') {
    $url = is_array($img) ? ($img['url'] ?? '') : (string) $img;
    $id = is_array($img) ? (int) ($img['id'] ?? 0) : 0;
    if ($id && !$alt) $alt = (string) get_post_meta($id, '_wp_attachment_image_alt', true);
    return '<div class="acs-device acs-device--' . esc_attr($kind) . ' ' . esc_attr($cls) . '"><div class="acs-device__screen">'
        . '<img src="' . esc_url($url) . '" alt="' . esc_attr($alt ?: 'AlcorCut Pro screenshot') . '" loading="lazy" decoding="async"></div><span class="acs-device__glare"></span></div>';
}

function acs_logo() {
    return '<img class="acs-logo__mark" src="' . esc_url(ACS_URL . 'assets/img/lens.svg') . '" alt="" width="34" height="34"><span class="acs-logo__name">AlcorCut<b> Pro</b></span>';
}

/** The store button: "Get it on Google Play" with a link, otherwise "Coming soon to Google Play". */
function acs_store_button($cls = '') {
    $u = acs_opt('play_url');
    if ($u) return '<a class="acs-btn acs-btn--primary ' . esc_attr($cls) . '" href="' . esc_url($u) . '">' . acs_line_icon('store') . '<span><small>Get it on</small>Google Play</span></a>';
    return '<span class="acs-btn acs-btn--primary acs-btn--soon ' . esc_attr($cls) . '" aria-disabled="true">' . acs_line_icon('store') . '<span><small>Coming soon to</small>Google Play</span></span>';
}

/* ------------------------------------------------------------------ base widget */

abstract class ACS_Base extends Widget_Base {
    public function get_categories() { return ['alcorcut']; }
    public function get_style_depends() { return ['acs-site']; }
    public function get_script_depends() { return ['acs-site']; }
    public function get_icon() { return 'eicon-star'; }
    public function get_keywords() { return ['alcorcut', 'app']; }

    /** Content fields: id => [type, label, default]. Types: text, textarea, rich, media, url, switch, select:<a|b>. */
    protected function fields() { return []; }
    /** Repeaters: id => [label, [field id => [type, label, default]], default rows, title field]. */
    protected function repeaters() { return []; }

    protected function add_field($id, $f, $target = null) {
        list($type, $label, $def) = $f;
        $map = ['text' => Controls_Manager::TEXT, 'textarea' => Controls_Manager::TEXTAREA, 'rich' => Controls_Manager::WYSIWYG,
            'media' => Controls_Manager::MEDIA, 'url' => Controls_Manager::TEXT, 'switch' => Controls_Manager::SWITCHER];
        $args = ['label' => $label, 'default' => $def, 'label_block' => true];
        if (strpos($type, 'select:') === 0) {
            $opts = [];
            foreach (explode('|', substr($type, 7)) as $o) $opts[$o] = ucfirst($o);
            $args['type'] = Controls_Manager::SELECT; $args['options'] = $opts; $args['label_block'] = false;
        } else {
            $args['type'] = $map[$type];
            if ($type === 'switch') { $args['return_value'] = 'yes'; $args['label_block'] = false; }
            if ($type === 'media') $args['dynamic'] = ['active' => true];
        }
        ($target ?: $this)->add_control($id, $args);
    }

    protected function register_controls() {
        $this->start_controls_section('acs_content', ['label' => 'Content', 'tab' => Controls_Manager::TAB_CONTENT]);
        foreach ($this->fields() as $id => $f) $this->add_field($id, $f);
        foreach ($this->repeaters() as $id => $r) {
            list($label, $rf, $rows, $title) = $r;
            $rep = new Repeater();
            foreach ($rf as $fid => $f) $this->add_field($fid, $f, $rep);
            $this->add_control($id, ['label' => $label, 'type' => Controls_Manager::REPEATER, 'fields' => $rep->get_controls(), 'default' => $rows, 'title_field' => '{{{ ' . $title . ' }}}']);
        }
        $this->end_controls_section();
    }

    protected function s() { return $this->get_settings_for_display(); }
}

/* ------------------------------------------------------------------ widgets */

class ACS_Nav extends ACS_Base {
    public function get_name() { return 'acs-nav'; }
    public function get_title() { return 'AlcorCut: Top bar'; }
    public function get_icon() { return 'eicon-nav-menu'; }
    protected function fields() { return ['solid' => ['switch', 'Solid background from the start', '']]; }
    protected function render() {
        $s = $this->s();
        $home = acs_page_url('home');
        $here = get_post_meta(get_the_ID(), '_acs_page', true);
        $a = function ($hash) use ($home, $here) { return $here === 'home' ? $hash : $home . $hash; };
        $links = [['Capture', $a('#capture')], ['Photo editor', $a('#photo')], ['Video editor', $a('#video')], ['Beta', $a('#beta')]];
        echo '<header class="acs acs-nav' . ($s['solid'] === 'yes' ? ' is-solid' : '') . '"><div class="acs-wrap acs-nav__in">';
        echo '<a class="acs-logo" href="' . esc_url($home) . '">' . acs_logo() . '</a>';
        echo '<nav class="acs-nav__links" aria-label="Main">';
        foreach ($links as $l) echo '<a href="' . esc_url($l[1]) . '">' . esc_html($l[0]) . '</a>';
        echo '</nav><span class="acs-nav__badge">Android</span>';
        echo '<button class="acs-nav__menu" type="button" aria-label="Menu" aria-expanded="false">' . acs_line_icon('menu') . '</button>';
        echo '</div><div class="acs-progress"><i></i></div></header>';
    }
}

class ACS_Hero extends ACS_Base {
    public function get_name() { return 'acs-hero'; }
    public function get_title() { return 'AlcorCut: Hero'; }
    public function get_icon() { return 'eicon-banner'; }
    protected function fields() {
        $c = acs_content()['hero'];
        return [
            'eyebrow' => ['text', 'Small line above the title', $c['eyebrow']],
            'title'   => ['textarea', 'Title (use <em>…</em> for the glowing part)', $c['title']],
            'text'    => ['textarea', 'Text', $c['text']],
            'film'    => ['text', 'Film button', $c['film']],
            'badges'  => ['textarea', 'Badges (one per line)', $c['badges']],
            'shot_a'  => ['media', 'Screenshot left', $c['shot_a']],
            'shot_b'  => ['media', 'Screenshot middle', $c['shot_b']],
            'shot_c'  => ['media', 'Screenshot right', $c['shot_c']],
        ];
    }
    protected function render() {
        $s = $this->s();
        echo '<section class="acs acs-hero"><div class="acs-aurora" aria-hidden="true"><i></i><i></i><i></i></div><div class="acs-grain" aria-hidden="true"></div>';
        echo '<div class="acs-wrap acs-hero__in"><div class="acs-hero__copy">';
        echo '<p class="acs-eyebrow" data-reveal>' . esc_html($s['eyebrow']) . '</p>';
        echo '<h1 class="acs-h1" data-reveal>' . acs_rich($s['title']) . '</h1>';
        echo '<p class="acs-lead" data-reveal>' . acs_rich($s['text']) . '</p>';
        echo '<div class="acs-hero__cta" data-reveal>' . acs_store_button() . '<a class="acs-btn acs-btn--ghost" href="#film">' . acs_line_icon('play') . '<span>' . esc_html($s['film']) . '</span></a></div>';
        echo '<ul class="acs-chips" data-reveal>';
        foreach (array_filter(array_map('trim', explode("\n", (string) $s['badges']))) as $b) echo '<li>' . esc_html($b) . '</li>';
        echo '</ul></div><div class="acs-hero__stage" data-tilt aria-hidden="true">';
        echo acs_device($s['shot_a'], 'phone', 'acs-hero__p acs-hero__p--a');
        echo acs_device($s['shot_b'], 'phone', 'acs-hero__p acs-hero__p--b');
        echo acs_device($s['shot_c'], 'phone', 'acs-hero__p acs-hero__p--c');
        echo '<div class="acs-hero__ring"></div></div></div>';
        echo '<a class="acs-scrollhint" href="#android" aria-label="Scroll down"><span></span></a></section>';
    }
}

class ACS_Stats extends ACS_Base {
    public function get_name() { return 'acs-stats'; }
    public function get_title() { return 'AlcorCut: Numbers'; }
    public function get_icon() { return 'eicon-counter'; }
    protected function repeaters() {
        return ['items' => ['Numbers', ['num' => ['text', 'Number', ''], 'label' => ['text', 'Label', '']], acs_content()['stats'], 'num']];
    }
    protected function render() {
        echo '<section class="acs acs-stats"><div class="acs-wrap acs-stats__in">';
        foreach ((array) $this->s()['items'] as $i => $it) {
            echo '<div class="acs-stat" data-reveal style="--d:' . (int) $i . '"><b data-count>' . esc_html($it['num']) . '</b><span>' . esc_html($it['label']) . '</span></div>';
        }
        echo '</div></section>';
    }
}

class ACS_Android extends ACS_Base {
    public function get_name() { return 'acs-android'; }
    public function get_title() { return 'AlcorCut: Android only'; }
    public function get_icon() { return 'eicon-device-mobile'; }
    protected function fields() {
        $c = acs_content()['android'];
        return [
            'eyebrow' => ['text', 'Small line above the title', $c['eyebrow']],
            'title'   => ['textarea', 'Title', $c['title']],
            'text'    => ['rich', 'Text', $c['text']],
            'basic'   => ['text', 'Word that gets crossed out', $c['basic']],
            'pro'     => ['text', 'Word that replaces it', $c['pro']],
        ];
    }
    protected function render() {
        $s = $this->s();
        echo '<section class="acs acs-android" id="android"><div class="acs-wrap acs-android__in">';
        echo '<div class="acs-android__word" data-reveal aria-hidden="true"><span class="acs-strike">' . esc_html($s['basic']) . '</span><span class="acs-glow">' . esc_html($s['pro']) . '</span></div>';
        echo '<div class="acs-android__copy"><p class="acs-eyebrow" data-reveal>' . esc_html($s['eyebrow']) . '</p>';
        echo '<h2 class="acs-h2" data-reveal>' . acs_rich($s['title']) . '</h2><div class="acs-prose" data-reveal>' . acs_rich($s['text']) . '</div></div>';
        echo '</div></section>';
    }
}

class ACS_Video extends ACS_Base {
    public function get_name() { return 'acs-video'; }
    public function get_title() { return 'AlcorCut: Vimeo film'; }
    public function get_icon() { return 'eicon-youtube'; }
    protected function fields() {
        $c = acs_content()['video'];
        return [
            'eyebrow' => ['text', 'Small line above the title', $c['eyebrow']],
            'title'   => ['text', 'Title', $c['title']],
            'text'    => ['textarea', 'Text', $c['text']],
            'vimeo'   => ['url', 'Vimeo link (e.g. https://vimeo.com/123456789)', $c['vimeo']],
            'soon'    => ['text', 'Text while there is no video', $c['soon']],
        ];
    }
    /** Vimeo player address from a vimeo.com link (also private links with a hash). Do Not Track is on. */
    public static function player($url) {
        if (!preg_match('~vimeo\.com/(?:video/|channels/[^/]+/|groups/[^/]+/videos/)?(\d+)(?:/([0-9a-f]+))?~i', (string) $url, $m)) return '';
        $q = ['dnt' => 1, 'title' => 0, 'byline' => 0, 'portrait' => 0, 'color' => 'ffb300', 'autoplay' => 1];
        if (!empty($m[2])) $q['h'] = $m[2];
        if (preg_match('~[?&]h=([0-9a-f]+)~i', (string) $url, $h)) $q['h'] = $h[1];
        return 'https://player.vimeo.com/video/' . $m[1] . '?' . http_build_query($q);
    }
    protected function render() {
        $s = $this->s();
        $src = self::player($s['vimeo']);
        echo '<section class="acs acs-video" id="film"><div class="acs-wrap"><div class="acs-head">';
        echo '<p class="acs-eyebrow" data-reveal>' . esc_html($s['eyebrow']) . '</p><h2 class="acs-h2" data-reveal>' . esc_html($s['title']) . '</h2><p class="acs-lead" data-reveal>' . esc_html($s['text']) . '</p></div>';
        echo '<div class="acs-player" data-reveal' . ($src ? ' data-src="' . esc_url($src) . '"' : '') . '>';
        echo '<div class="acs-player__frame"><span class="acs-player__scan"></span>';
        if ($src) echo '<button class="acs-player__play" type="button" aria-label="Play the film">' . acs_line_icon('play') . '</button><span class="acs-player__note">Plays from Vimeo</span>';
        else echo '<span class="acs-player__play is-idle">' . acs_line_icon('play') . '</span><span class="acs-player__note">' . esc_html($s['soon']) . '</span>';
        echo '</div></div></div></section>';
    }
}

class ACS_Trio extends ACS_Base {
    public function get_name() { return 'acs-trio'; }
    public function get_title() { return 'AlcorCut: Three apps in one'; }
    public function get_icon() { return 'eicon-gallery-grid'; }
    protected function repeaters() {
        return ['items' => ['Cards', ['key' => ['select:capture|photo|video', 'Icon', 'capture'], 'title' => ['text', 'Title', ''], 'text' => ['textarea', 'Text', ''], 'link' => ['url', 'Link', '#']], acs_content()['trio'], 'title']];
    }
    protected function render() {
        echo '<section class="acs acs-trio"><div class="acs-wrap acs-trio__in">';
        foreach ((array) $this->s()['items'] as $i => $it) {
            echo '<a class="acs-card acs-trio__card" data-reveal data-glow style="--d:' . (int) $i . '" href="' . esc_url($it['link']) . '">' . acs_line_icon($it['key'])
                . '<h3>' . esc_html($it['title']) . '</h3><p>' . esc_html($it['text']) . '</p><span class="acs-more">Why it’s pro ' . acs_line_icon('arrow') . '</span></a>';
        }
        echo '</div></section>';
    }
}

class ACS_Pro extends ACS_Base {
    public function get_name() { return 'acs-pro'; }
    public function get_title() { return 'AlcorCut: Why it’s pro'; }
    public function get_icon() { return 'eicon-check-circle'; }
    protected function fields() {
        $c = acs_content()['pro']['capture'];
        return [
            'anchor'  => ['text', 'Section id (for links like #capture)', $c['anchor']],
            'eyebrow' => ['text', 'Small line above the title', $c['eyebrow']],
            'title'   => ['textarea', 'Title', $c['title']],
            'text'    => ['textarea', 'Text', $c['text']],
            'shot'    => ['media', 'Screenshot', $c['shot']],
            'more'    => ['select:capture|photo|video', '“…and much more” opens the feature page for', $c['more']],
            'flip'    => ['switch', 'Picture on the right', $c['flip']],
        ];
    }
    protected function repeaters() {
        $c = acs_content()['pro']['capture'];
        return ['reasons' => ['Reasons', ['title' => ['text', 'Title', ''], 'text' => ['textarea', 'Text', '']], $c['reasons'], 'title']];
    }
    protected function render() {
        $s = $this->s();
        echo '<section class="acs acs-pro' . ($s['flip'] === 'yes' ? ' is-flip' : '') . '" id="' . esc_attr($s['anchor']) . '"><div class="acs-wrap acs-pro__in">';
        echo '<div class="acs-pro__media" data-reveal data-tilt>' . acs_device($s['shot'], 'phone', 'acs-pro__phone') . '<div class="acs-pro__orb" aria-hidden="true"></div></div>';
        echo '<div class="acs-pro__copy"><p class="acs-eyebrow" data-reveal>' . esc_html($s['eyebrow']) . '</p><h2 class="acs-h2" data-reveal>' . acs_rich($s['title']) . '</h2><p class="acs-lead" data-reveal>' . esc_html($s['text']) . '</p><ol class="acs-reasons">';
        foreach ((array) $s['reasons'] as $i => $r) {
            echo '<li data-reveal style="--d:' . (int) $i . '"><span class="acs-reasons__n">' . sprintf('%02d', $i + 1) . '</span><div><h3>' . esc_html($r['title']) . '</h3><p>' . esc_html($r['text']) . '</p></div></li>';
        }
        echo '</ol><a class="acs-much" data-reveal href="' . esc_url(acs_page_url($s['more'])) . '"><span>…and much more</span>' . acs_line_icon('arrow') . '</a></div>';
        echo '</div></section>';
    }
}

class ACS_Gallery extends ACS_Base {
    public function get_name() { return 'acs-gallery'; }
    public function get_title() { return 'AlcorCut: Screenshots'; }
    public function get_icon() { return 'eicon-slider-push'; }
    protected function fields() {
        $c = acs_content()['gallery'];
        return ['eyebrow' => ['text', 'Small line above the title', $c['eyebrow']], 'title' => ['textarea', 'Title', $c['title']]];
    }
    protected function repeaters() {
        return ['shots' => ['Screenshots', ['image' => ['media', 'Screenshot', acs_img('shot-capture.svg')], 'caption' => ['text', 'Caption', '']], acs_content()['gallery']['shots'], 'caption']];
    }
    protected function render() {
        $s = $this->s();
        echo '<section class="acs acs-gallery"><div class="acs-wrap"><div class="acs-head acs-head--row"><div><p class="acs-eyebrow" data-reveal>' . esc_html($s['eyebrow']) . '</p><h2 class="acs-h2" data-reveal>' . acs_rich($s['title']) . '</h2></div>';
        echo '<div class="acs-gallery__nav"><button type="button" class="acs-round" data-dir="-1" aria-label="Previous">' . acs_line_icon('arrow') . '</button><button type="button" class="acs-round" data-dir="1" aria-label="Next">' . acs_line_icon('arrow') . '</button></div></div></div>';
        echo '<div class="acs-gallery__track" tabindex="0">';
        foreach ((array) $s['shots'] as $i => $it) {
            echo '<figure class="acs-gallery__item" data-reveal style="--d:' . (int) ($i % 4) . '">' . acs_device($it['image'], 'phone', '', $it['caption']) . '<figcaption>' . esc_html($it['caption']) . '</figcaption></figure>';
        }
        echo '</div></section>';
    }
}

class ACS_Cards extends ACS_Base {
    public function get_name() { return 'acs-cards'; }
    public function get_title() { return 'AlcorCut: Feature cards'; }
    public function get_icon() { return 'eicon-info-box'; }
    protected function fields() {
        $c = acs_content()['cards'];
        return ['eyebrow' => ['text', 'Small line above the title', $c['eyebrow']], 'title' => ['textarea', 'Title', $c['title']]];
    }
    protected function repeaters() {
        return ['items' => ['Cards', ['icon' => ['select:chip|lock|flow|watch|screens|globe|capture|photo|video', 'Icon', 'chip'], 'title' => ['text', 'Title', ''], 'text' => ['textarea', 'Text', '']], acs_content()['cards']['items'], 'title']];
    }
    protected function render() {
        $s = $this->s();
        echo '<section class="acs acs-cards"><div class="acs-wrap"><div class="acs-head"><p class="acs-eyebrow" data-reveal>' . esc_html($s['eyebrow']) . '</p><h2 class="acs-h2" data-reveal>' . acs_rich($s['title']) . '</h2></div><div class="acs-cards__grid">';
        foreach ((array) $s['items'] as $i => $it) {
            echo '<div class="acs-card" data-reveal data-glow style="--d:' . (int) ($i % 3) . '">' . acs_line_icon($it['icon']) . '<h3>' . esc_html($it['title']) . '</h3><p>' . esc_html($it['text']) . '</p></div>';
        }
        echo '</div></div></section>';
    }
}

class ACS_Beta extends ACS_Base {
    public function get_name() { return 'acs-beta'; }
    public function get_title() { return 'AlcorCut: Beta testing'; }
    public function get_icon() { return 'eicon-person'; }
    protected function fields() {
        $c = acs_content()['beta'];
        return ['title' => ['text', 'Title', $c['title']], 'text' => ['text', 'Text', $c['text']], 'button' => ['text', 'Button', $c['button']], 'reward' => ['text', 'Small text under the button', $c['reward']]];
    }
    protected function render() {
        $s = $this->s();
        $u = acs_opt('beta_url') ?: acs_opt('facebook');
        echo '<section class="acs acs-beta" id="beta"><div class="acs-wrap"><div class="acs-beta__box" data-reveal data-glow>';
        echo '<div class="acs-beta__copy"><h2>' . esc_html($s['title']) . '</h2><p>' . esc_html($s['text']) . '</p></div>';
        echo '<div class="acs-beta__act"><a class="acs-btn acs-btn--primary" href="' . esc_url($u ?: '#beta') . '"' . ($u ? ' target="_blank" rel="noopener"' : ' aria-disabled="true"') . '><span>' . esc_html($s['button']) . '</span>' . acs_line_icon('arrow') . '</a>';
        echo '<small class="acs-beta__reward">' . esc_html($s['reward']) . '</small></div></div></div></section>';
    }
}

class ACS_Share extends ACS_Base {
    public function get_name() { return 'acs-share'; }
    public function get_title() { return 'AlcorCut: Share this page'; }
    public function get_icon() { return 'eicon-share'; }
    protected function fields() { return ['title' => ['text', 'Title', acs_content()['share']['title']]]; }
    protected function render() {
        $s = $this->s();
        $nets = [
            'facebook' => ['Facebook', 'https://www.facebook.com/sharer/sharer.php?u={u}'],
            'x'        => ['X', 'https://x.com/intent/post?url={u}&text={t}'],
            'whatsapp' => ['WhatsApp', 'https://wa.me/?text={t}%20{u}'],
            'linkedin' => ['LinkedIn', 'https://www.linkedin.com/sharing/share-offsite/?url={u}'],
            'reddit'   => ['Reddit', 'https://www.reddit.com/submit?url={u}&title={t}'],
            'threads'  => ['Threads', 'https://www.threads.net/intent/post?text={t}%20{u}'],
            'mail'     => ['Email', 'mailto:?subject={t}&body={u}'],
        ];
        echo '<section class="acs acs-share"><div class="acs-wrap acs-share__in"><h2>' . esc_html($s['title']) . '</h2><div class="acs-share__row">';
        echo '<button type="button" class="acs-share__btn acs-share__native" hidden>' . acs_icon('share') . '<span>Share…</span></button>';
        foreach ($nets as $k => $n) echo '<a class="acs-share__btn" data-share="' . esc_attr($n[1]) . '" href="#" target="_blank" rel="noopener" aria-label="Share on ' . esc_attr($n[0]) . '" title="' . esc_attr($n[0]) . '">' . acs_icon($k) . '</a>';
        echo '<button type="button" class="acs-share__btn acs-share__copy" aria-label="Copy link" title="Copy link">' . acs_icon('link') . '<span>Copy link</span></button>';
        echo '</div></div></section>';
    }
}

class ACS_Footer extends ACS_Base {
    public function get_name() { return 'acs-footer'; }
    public function get_title() { return 'AlcorCut: Footer'; }
    public function get_icon() { return 'eicon-footer'; }
    protected function fields() {
        $c = acs_content()['footer'];
        return ['tagline' => ['text', 'Tagline', $c['tagline']], 'note' => ['text', 'Small note', $c['note']]];
    }
    protected function render() {
        $s = $this->s();
        $soc = ['facebook' => 'Facebook', 'youtube' => 'YouTube', 'instagram' => 'Instagram', 'tiktok' => 'TikTok'];
        echo '<footer class="acs acs-footer"><div class="acs-wrap"><div class="acs-footer__top">';
        echo '<div><a class="acs-logo" href="' . esc_url(acs_page_url('home')) . '">' . acs_logo() . '</a><p>' . esc_html($s['tagline']) . '</p></div>';
        echo '<nav class="acs-footer__links" aria-label="Features"><a href="' . esc_url(acs_page_url('capture')) . '">Capture features</a><a href="' . esc_url(acs_page_url('photo')) . '">Photo editor features</a><a href="' . esc_url(acs_page_url('video')) . '">Video editor features</a></nav>';
        echo '<div class="acs-social">';
        foreach ($soc as $k => $label) {
            $u = acs_opt($k);
            echo '<a class="acs-social__a" href="' . esc_url($u ?: '#') . '"' . ($u ? ' target="_blank" rel="noopener"' : ' aria-disabled="true"') . ' aria-label="' . esc_attr($label) . '" title="' . esc_attr($label) . '">' . acs_icon($k) . '</a>';
        }
        echo '</div></div><div class="acs-footer__bottom"><span>© ' . do_shortcode('[alcorcut_year]') . ' ' . esc_html(acs_opt('holder')) . '. All rights reserved.</span><span class="acs-footer__note">' . esc_html($s['note']) . '</span></div></div></footer>';
    }
}

class ACS_Features extends ACS_Base {
    public function get_name() { return 'acs-features'; }
    public function get_title() { return 'AlcorCut: Full feature list'; }
    public function get_icon() { return 'eicon-bullet-list'; }
    protected function fields() {
        $c = acs_content()['features']['capture'];
        return [
            'list'  => ['select:capture|photo|video', 'Which feature list', 'capture'],
            'title' => ['text', 'Title (empty = the standard title)', ''],
            'text'  => ['textarea', 'Text (empty = the standard text)', ''],
        ];
    }
    protected function render() {
        $s = $this->s();
        $k = in_array($s['list'], ['capture', 'photo', 'video'], true) ? $s['list'] : 'capture';
        $c = acs_content()['features'][$k];
        $groups = json_decode((string) file_get_contents(ACS_DIR . 'data/features-' . $k . '.json'), true) ?: [];
        $n = 0; foreach ($groups as $g) $n += count($g['items']);
        $tabs = ['capture' => 'Capture', 'photo' => 'Photo editor', 'video' => 'Video editor'];
        echo '<section class="acs acs-features"><div class="acs-aurora acs-aurora--soft" aria-hidden="true"><i></i><i></i></div><div class="acs-wrap">';
        echo '<a class="acs-back" href="' . esc_url(acs_page_url('home')) . '#' . esc_attr($k) . '">← Back to the overview</a>';
        echo '<div class="acs-features__head"><div><p class="acs-eyebrow" data-reveal>' . acs_line_icon($k) . ' All features</p>';
        echo '<h1 class="acs-h2" data-reveal>' . esc_html($s['title'] ?: $c['title']) . '</h1><p class="acs-lead" data-reveal>' . esc_html($s['text'] ?: $c['text']) . '</p></div>';
        echo '<nav class="acs-tabs" data-reveal aria-label="Feature lists">';
        foreach ($tabs as $tk => $tl) echo '<a class="' . ($tk === $k ? 'is-on' : '') . '" href="' . esc_url(acs_page_url($tk)) . '">' . acs_line_icon($tk) . esc_html($tl) . '</a>';
        echo '</nav></div>';
        echo '<div class="acs-features__tools" data-reveal><label class="acs-search">' . acs_line_icon('search') . '<input type="search" placeholder="Search ' . (int) $n . ' features…" aria-label="Search features"></label>';
        echo '<div class="acs-jump">';
        foreach ($groups as $i => $g) echo '<a href="#g-' . $k . '-' . $i . '">' . esc_html($g['title']) . '</a>';
        echo '</div><p class="acs-features__count" aria-live="polite"></p></div>';
        echo '<div class="acs-groups">';
        foreach ($groups as $i => $g) {
            echo '<section class="acs-group" id="g-' . $k . '-' . $i . '" data-reveal><h2><span>' . esc_html($g['title']) . '</span><small>' . count($g['items']) . '</small></h2><ul>';
            foreach ($g['items'] as $it) echo '<li>' . wp_kses($it, ['strong' => [], 'code' => [], 'em' => []]) . '</li>';
            echo '</ul></section>';
        }
        echo '</div><p class="acs-features__empty" hidden>No feature matches that search.</p></div></section>';
    }
}
