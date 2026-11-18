"""Reviewed upstream versions for the native SeedCracker port matrix."""

FAMILIES = {
    '1204': ('1.20.2', '1.20.3', '1.20.4'),
    '1206': ('1.20.5', '1.20.6', '1.21', '1.21.1'),
    '1214': ('1.21.2', '1.21.3', '1.21.4'),
    '1218': ('1.21.5', '1.21.6', '1.21.7', '1.21.8'),
    '12111': ('1.21.9', '1.21.10', '1.21.11'),
    '263': ('26.1', '26.1.1', '26.1.2', '26.2', '26.3'),
}


def upstream_for(mc):
    if mc in FAMILIES['1204']: return '2.14.6'
    if mc in ('1.20.5', '1.20.6'): return '2.14.7'
    if mc in ('1.21', '1.21.1'): return '2.15.0'
    if mc in ('1.21.2', '1.21.3'): return '2.15.1'
    if mc == '1.21.4': return '2.15.2'
    if mc == '1.21.5': return '2.15.3'
    if mc in ('1.21.6', '1.21.7', '1.21.8'): return '2.15.4'
    if mc in ('1.21.9', '1.21.10'): return '2.15.5'
    if mc == '1.21.11': return '2.15.6'
    if mc in ('26.1', '26.1.1', '26.1.2'): return '2.16.0'
    if mc == '26.2': return '2.16.1'
    if mc == '26.3': return '2.16.2'
    raise ValueError('Unreviewed native SeedCracker version: ' + mc)


def cloth_for(mc):
    if mc == '1.21.5': return '18.0.145'
    if mc in ('1.21.6', '1.21.7', '1.21.8'): return '19.0.147'
    if mc in ('1.21.9', '1.21.10'): return '20.0.149'
    if mc == '1.21.11': return '21.11.153'
    if mc in ('26.1', '26.1.1', '26.1.2'): return '26.1.154'
    if mc == '26.2': return '26.2.155'
    if mc == '26.3': return '26.3.159'
    raise ValueError('No extracted Cloth API for ' + mc)
