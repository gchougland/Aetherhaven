# Addon housing regression assets

These unmodified Villagers, Dialogue and Quests JSON files were copied from
CozyTales-Fishing and Machinaria on 2026-10-19. They reproduce the catalog reload
failure caused by treating Machinaria_Mechanic_Rescue as a town resident, and
protect both addons' existing housing quest IDs, dialogue and saved progress.

CoreVillagerHousingAddonTest loads each addon separately and together with the
core catalogs. It also checks the current sibling repositories when available;
these bundled snapshots keep the regression covered on CI without those repos.
