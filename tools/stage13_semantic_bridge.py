"""Compare complete retained/current projections without hashing temporary paths.

Only each database's ``derived_copy`` path is auxiliary. Original projections
and their opaque artifact hashes remain unchanged. This module performs no I/O.
The existing custody comparators still decide captured-surface/SQLite equality;
exact JSON comparison additionally preserves bool/int/float distinctions.
"""

import json


ERROR_CODE = "RETAINED_WINDOW_COMPLETE_SEMANTIC_PROJECTION_DIFFERS"
DATABASE_FIELDS = frozenset(
    ("logical", "physical", "wal_validation", "counts", "derived_copy", "comparison_scope")
)


class ProjectionMismatch(ValueError):
    """The complete captured projections differ or are malformed."""

    code = ERROR_CODE

    def __init__(self):
        super().__init__(self.code)


def _require(condition):
    if not condition:
        raise ProjectionMismatch()


def _json_value(value):
    """Accept JSON values only; don't silently coerce tuples or nonstring keys."""
    if type(value) is dict:
        _require(all(type(key) is str for key in value))
        for item in value.values():
            _json_value(item)
    elif type(value) is list:
        for item in value:
            _json_value(item)
    else:
        _require(value is None or type(value) in (str, bool, int, float))


def _canonical(value):
    _json_value(value)
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)
    except (ValueError, TypeError):
        raise ProjectionMismatch() from None


def compare_recovered_projection(custody_module, recovered, current):
    """Keep R6's existing comparisons and exclude only database copy paths.

    ``custody_module`` supplies the already reviewed, pure ``compare_captures``
    and ``compare_sqlite`` functions. No historical digest is normalized or
    substituted for either complete input object.
    """
    _require(type(recovered) is dict and type(current) is dict)
    _require(set(recovered) == set(current))
    for field in ("databases", "preferences", "files", "roles"):
        _require(type(recovered.get(field)) is dict and type(current.get(field)) is dict)
    _require(set(recovered["databases"]) == set(current["databases"]))
    _canonical(recovered)
    _canonical(current)
    for name in recovered["databases"]:
        before, after = recovered["databases"][name], current["databases"][name]
        _require(type(before) is dict and type(after) is dict)
        _require(set(before) == set(after) == DATABASE_FIELDS)
        _require(type(before["derived_copy"]) is str and type(after["derived_copy"]) is str)
        _require(
            _canonical({key: value for key, value in before.items() if key != "derived_copy"})
            == _canonical({key: value for key, value in after.items() if key != "derived_copy"})
        )
    _require(
        _canonical({key: value for key, value in recovered.items() if key != "databases"})
        == _canonical({key: value for key, value in current.items() if key != "databases"})
    )
    observed = custody_module.compare_captures(recovered, current)
    _require(type(observed) is dict and observed.get("captured_surface_equal") is True)
    for name in recovered["databases"]:
        sql = custody_module.compare_sqlite(recovered["databases"][name], current["databases"][name])
        _require(
            sql["logical_equal"] is True and sql["physical_equal"] is True
            and sql["before_logical_sha256"] == sql["after_logical_sha256"]
        )
    return {
        "existing_comparison": observed,
        "all_complete_projection_fields_typed_equal": True,
        "only_derived_copy_path_excluded_from_equality": True,
        "full_source_objects_preserved": True,
        "hashes_normalized": False,
    }
