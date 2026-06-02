# Architecture
Compose UI calls offline domain services. WorkManager downloads private verified assets. Local tour packs provide route, POIs, triggers, stories, sources, map data, and RAG chunks. The production persistence target is Room plus DataStore; the initial compileable UI keeps setup state in-process while the domain rules remain isolated and tested.
