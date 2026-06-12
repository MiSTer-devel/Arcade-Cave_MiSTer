local out_path = os.getenv("MAME_PWRINST2_SPACE_PROBE_OUT") or "pwrinst2_space_probe.txt"
local out = assert(io.open(out_path, "w"))
out:setvbuf("line")

local machine = manager.machine

local function dump_device(tag)
  local dev = machine.devices[tag]
  out:write("device " .. tag .. " " .. tostring(dev) .. "\n")
  if dev and dev.spaces then
    for name, space in pairs(dev.spaces) do
      out:write("  space " .. tostring(name) .. " " .. tostring(space) .. "\n")
    end
  end
end

dump_device(":maincpu")
dump_device(":audiocpu")
dump_device(":oki1")
dump_device(":oki2")
dump_device(":nmk112")
dump_device(":ymsnd")

out:close()
machine:exit()
