# Release builds strip unused code. Room, Compose, and coroutines ship their own
# rules, and everything the manifest names is kept automatically, so nothing
# extra is needed yet. Add keep rules here only for code reached by reflection.
