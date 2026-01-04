-- live-reload.lua
-- mGBA Lua script for automatic ROM reloading during development
--
-- This script monitors the ROM file for changes and automatically reloads
-- when a new build is detected. Use with Gradle continuous build:
--   Terminal 1: ./gradlew -t buildRom
--   Terminal 2: ./gradlew runEmulator
--
-- Platform support:
--   - macOS: uses stat -f %m
--   - Linux: uses stat -c %Y
--   - Windows: uses PowerShell

-- Configuration
local CHECK_INTERVAL = 30  -- Check every N frames (30 = ~0.5 seconds at 60fps)
local RELOAD_DELAY = 10    -- Frames to wait after detecting change before reload

-- State
local rom_path = nil
local last_modified = 0
local frame_counter = 0
local pending_reload = false
local reload_countdown = 0

-- Detect platform and get appropriate stat command
local function get_platform()
    local sep = package.config:sub(1, 1)
    if sep == "\\" then
        return "windows"
    else
        -- Check for macOS vs Linux
        local handle = io.popen("uname -s 2>/dev/null")
        if handle then
            local result = handle:read("*a")
            handle:close()
            if result:match("Darwin") then
                return "macos"
            end
        end
        return "linux"
    end
end

local PLATFORM = get_platform()

-- Get file modification time based on platform
local function get_file_mtime(path)
    local handle
    local result

    if PLATFORM == "macos" then
        -- macOS: stat -f %m returns modification time as Unix timestamp
        handle = io.popen('stat -f %m "' .. path .. '" 2>/dev/null')
    elseif PLATFORM == "linux" then
        -- Linux: stat -c %Y returns modification time as Unix timestamp
        handle = io.popen('stat -c %Y "' .. path .. '" 2>/dev/null')
    else
        -- Windows: Use PowerShell to get LastWriteTime
        local ps_cmd = 'powershell -Command "(Get-Item \'' .. path:gsub("'", "''") .. '\').LastWriteTime.Ticks"'
        handle = io.popen(ps_cmd)
    end

    if handle then
        result = handle:read("*a")
        handle:close()
        return tonumber(result)
    end

    return nil
end

-- Initialize ROM path from currently loaded ROM
local function init_rom_path()
    if emu and emu.getGameTitle then
        -- Try to get ROM info from emulator
        local title = emu:getGameTitle()
        if title and title ~= "" then
            console:log("[live-reload] Monitoring ROM: " .. title)
        end
    end

    -- ROM path should be passed via script argument or detected
    -- For now, we'll try common locations relative to script
    if rom_path then
        local mtime = get_file_mtime(rom_path)
        if mtime then
            last_modified = mtime
            console:log("[live-reload] Initial mtime: " .. tostring(mtime))
            console:log("[live-reload] Platform: " .. PLATFORM)
            console:log("[live-reload] Watching: " .. rom_path)
        else
            console:error("[live-reload] Cannot access ROM file: " .. rom_path)
        end
    end
end

-- Frame callback - check for file changes
local function on_frame()
    frame_counter = frame_counter + 1

    -- Handle pending reload with delay
    if pending_reload then
        reload_countdown = reload_countdown - 1
        if reload_countdown <= 0 then
            pending_reload = false
            console:log("[live-reload] Reloading ROM...")
            if emu and emu.loadFile then
                emu:loadFile(rom_path)
                console:log("[live-reload] ROM reloaded successfully!")
            end
            -- Update mtime after reload
            local mtime = get_file_mtime(rom_path)
            if mtime then
                last_modified = mtime
            end
        end
        return
    end

    -- Only check at specified interval
    if frame_counter % CHECK_INTERVAL ~= 0 then
        return
    end

    if not rom_path then
        return
    end

    -- Check file modification time
    local current_mtime = get_file_mtime(rom_path)

    if current_mtime and current_mtime > last_modified then
        console:log("[live-reload] Change detected! Scheduling reload...")
        pending_reload = true
        reload_countdown = RELOAD_DELAY
    end
end

-- Set ROM path for monitoring
-- Call this from mGBA scripting console: live_reload_set_path("/path/to/game.gb")
function live_reload_set_path(path)
    rom_path = path
    console:log("[live-reload] ROM path set to: " .. path)
    init_rom_path()
end

-- Allow configuration via global variable (set before loading script)
if GBKT_ROM_PATH then
    rom_path = GBKT_ROM_PATH
end

-- Register callbacks
if callbacks then
    callbacks:add("frame", on_frame)
    console:log("[live-reload] Script loaded - " .. PLATFORM .. " platform")
    console:log("[live-reload] Checking every " .. CHECK_INTERVAL .. " frames")

    if rom_path then
        init_rom_path()
    else
        console:log("[live-reload] No ROM path set. Use live_reload_set_path('/path/to/game.gb')")
        console:log("[live-reload] Or set GBKT_ROM_PATH before loading script")
    end
else
    print("[live-reload] Error: mGBA callbacks not available")
end
