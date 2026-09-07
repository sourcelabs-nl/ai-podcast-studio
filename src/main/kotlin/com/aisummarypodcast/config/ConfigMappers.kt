package com.aisummarypodcast.config

/**
 * The models a podcast may choose, grouped by provider.
 *
 * An unselectable entry is withheld: its pricing is still needed to resolve the cost of episodes
 * already generated on it, but it can no longer serve a request (see [ModelCost.selectable]).
 */
internal fun Map<String, Map<String, ModelCost>>.toSelectableModels(): Map<String, List<AvailableModel>> =
    mapValues { (_, models) ->
        models.filterValues { it.selectable }
            .map { (name, cost) -> AvailableModel(name = name, type = cost.type.name.lowercase()) }
    }
