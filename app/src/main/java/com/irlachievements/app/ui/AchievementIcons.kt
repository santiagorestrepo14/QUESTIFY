package com.irlachievements.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.irlachievements.app.domain.model.AchievementDefinition
import com.irlachievements.app.domain.model.RuleType

internal fun achievementIcon(definition: AchievementDefinition): ImageVector = when (definition.id) {
    "color_echo" -> Icons.Default.Palette
    "quiet_signal" -> Icons.Default.TouchApp
    "ascending_path" -> Icons.Default.FormatListNumbered
    "odd_symbol" -> Icons.Default.FindInPage
    "stereo_compass" -> Icons.Default.Headphones
    "voice_balance" -> Icons.Default.GraphicEq
    "clap_echo" -> Icons.Default.MusicNote
    "acoustic_key" -> Icons.Default.Lock
    "silent_path" -> Icons.Default.VolumeOff
    "sound_pairs" -> Icons.Default.Headphones
    "tone_ladder" -> Icons.Default.MusicNote
    "sound_detective" -> Icons.Default.GraphicEq
    "fleeting_route" -> Icons.Default.Route
    "inverse_rule" -> Icons.Default.SwapVert
    "subtle_change" -> Icons.Default.FindInPage
    "in_place" -> Icons.Default.Extension
    "first_steps", "walk_it_off" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "going_somewhere" -> Icons.Default.Hiking
    "touch_grass" -> Icons.Default.Grass
    "still_going" -> Icons.Default.Moving
    "long_day" -> Icons.Default.WbSunny
    "my_legs_hurt" -> Icons.Default.FitnessCenter
    "please_sit_down" -> Icons.Default.AirlineSeatReclineNormal
    "weekend_warrior" -> Icons.Default.Weekend
    "night_walker" -> Icons.Default.NightsStay
    "before_breakfast" -> Icons.Default.FreeBreakfast
    "overachiever" -> Icons.Default.RocketLaunch
    "what_were_you_doing" -> Icons.AutoMirrored.Filled.HelpOutline

    "getting_started" -> Icons.Default.Flag
    "walker" -> Icons.Default.Hiking
    "wanderer" -> Icons.Default.Explore
    "millionaire" -> Icons.Default.WorkspacePremium
    "five_million_club" -> Icons.Default.Diamond
    "consistency", "touch_grass_week", "touch_grass_month" -> Icons.Default.LocalFireDepartment
    "daily_5k", "daily_10k", "halfway_there" -> Icons.Default.Route
    "long_haul", "nomad", "road_to_1000", "traveler" -> Icons.Default.Explore
    "run", "relay_race", "park_and_run", "sudden_sprint" -> Icons.AutoMirrored.Filled.DirectionsRun
    "two_wheels", "human_power", "switchback" -> Icons.AutoMirrored.Filled.DirectionsBike
    "passenger", "pit_stop", "home_stretch" -> Icons.Default.DirectionsCar
    "multiclass", "the_trinity", "five_modes" -> Icons.Default.Dashboard
    "back_on_your_feet", "gear_change", "accelerating", "decelerating", "door_to_door",
    "without_engine", "three_terrains", "cool_down", "reverse_gear" -> Icons.AutoMirrored.Filled.AltRoute

    "hello_again", "frequent_visitor", "century_unlocks", "single_visit", "late_start",
    "morning_only", "office_hours", "night_shift", "hourglass", "rapid_fire", "deliberate" -> Icons.Default.PhoneAndroid
    "leave_me_alone", "digital_detox", "monk_mode", "controlled", "ghost_mode", "untouched" -> Icons.Default.DoNotDisturbOn

    "hello_world" -> Icons.Default.WavingHand
    "welcome_back", "where_did_you_go", "prodigal_son", "low_power_return" -> Icons.Default.Replay
    "one_week_later", "one_month_later", "centurion", "committed", "regular", "seasoned", "year_one" -> Icons.Default.CalendarMonth
    "getting_nervous", "living_dangerously", "one_percent_club", "life_support", "full_cycle" -> Icons.Default.BatteryAlert
    "overcharged", "wireless", "weekend_wireless", "dock_worker", "power_trinity", "anniversary_charge" -> Icons.Default.ElectricBolt
    "xp_hunter" -> Icons.Default.Bolt
    "challenge_rookie", "challenge_regular", "challenge_elite" -> Icons.Default.SportsScore

    "witching_hour", "nice", "leet", "twelve_thirty_four", "not_found", "midnight_club",
    "early_bird", "night_owl", "mirror_time", "prime_time" -> Icons.Default.Schedule
    "new_year_same_phone", "unlucky", "leap_of_faith", "calendar_echo", "date_stamp" -> Icons.Default.Event
    "battery_synchronized", "day_synchronized", "perfect_count", "checksum", "triple_match",
    "clock_deja_vu" -> Icons.Default.Sync

    "full_circle", "slow_motion", "speedrun", "flat_spin", "spin_doctor" -> Icons.AutoMirrored.Filled.RotateRight
    "do_it_backwards" -> Icons.AutoMirrored.Filled.RotateLeft
    "double_trouble" -> Icons.Default.Sync
    "beyblade" -> Icons.Default.BlurCircular
    "let_it_rip" -> Icons.Default.AllInclusive
    "triple_axis", "xyz", "axis_architect" -> Icons.Default.ViewInAr
    "steady_hand", "rock_solid", "spirit_level", "perfectionist", "dont_move", "the_bomb",
    "stillness_master" -> Icons.Default.Balance
    "upside_down", "standing_tall", "left_right_left", "nod_yes", "flip_flop",
    "orientation_master" -> Icons.Default.ScreenRotation
    "shake_it", "shake_it_off", "are_you_okay", "shake_therapy" -> Icons.Default.Vibration
    "north_star", "four_winds", "wrong_way_around", "human_compass" -> Icons.Default.Explore
    "simon_says", "simon_says_ii", "clone", "photographic_memory", "memory_palace" -> Icons.Default.Psychology
    "target_practice", "bullseye", "perfect_landing", "zero_point_zero" -> Icons.Default.CenterFocusStrong
    "secret_code", "false_start", "sequence_breaker" -> Icons.Default.Password
    "traffic_light" -> Icons.Default.Traffic
    "duel", "frame_perfect", "photo_finish", "reaction_master" -> Icons.Default.Speed
    "human_metronome", "rhythm", "rhythm_section", "are_you_a_robot" -> Icons.Default.MusicNote
    "tilt_labyrinth" -> Icons.Default.Games
    "vampire" -> Icons.Default.DarkMode
    "supernova" -> Icons.Default.LightMode
    "dry_air", "humid_air" -> Icons.Default.WaterDrop
    "cool_air", "warm_air" -> Icons.Default.Thermostat
    "low_pressure", "high_pressure" -> Icons.Default.Speed
    "light_and_shadow" -> Icons.Default.Contrast
    "final_boss", "perfect_run", "boss_rush", "final_form", "perfect_combo" -> Icons.Default.MilitaryTech

    "against_all_odds", "one_shot", "persistent", "definition_of_insanity", "deja_vu",
    "three_am_gamer", "premature", "redemption", "so_close", "cold_blooded" -> Icons.Default.Stars
    "all_rounder", "device_master", "gyroscope_master", "balanced_build" -> Icons.Default.WorkspacePremium
    "rarity_rainbow" -> Icons.Default.Palette
    "legendary_collection", "secret_hunter", "treasure_hunter", "completionist" -> Icons.Default.EmojiEvents
    "triple_crown" -> Icons.Default.Filter3
    "the_answer" -> Icons.Default.QuestionMark
    "glitch_in_reality" -> Icons.Default.AutoAwesome

    else -> when (definition.ruleType) {
        RuleType.CHALLENGE_COMPLETED, RuleType.CHALLENGE_META -> Icons.Default.SportsEsports
        RuleType.BATTERY_AT_MOST, RuleType.BATTERY_CYCLE -> Icons.Default.BatteryAlert
        RuleType.TIME_WINDOW -> Icons.Default.Schedule
        RuleType.CALENDAR_DATE, RuleType.SPECIAL_DATE -> Icons.Default.Event
        RuleType.DAILY_STEPS, RuleType.TOTAL_STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
        else -> Icons.Default.EmojiEvents
    }
}
