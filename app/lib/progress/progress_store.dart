import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Observer pattern: screens listen to this store and rebuild when progress changes.
class ProgressStore extends ChangeNotifier {
  ProgressStore(this._prefs)
      : _read = {...?_prefs.getStringList(_readKey)},
        _saved = {...?_prefs.getStringList(_savedKey)},
        _lastOpened = _prefs.getString(_lastKey);

  static const _readKey = 'progress.read';
  static const _savedKey = 'progress.saved';
  static const _lastKey = 'progress.last';

  final SharedPreferences _prefs;
  final Set<String> _read;
  final Set<String> _saved;
  String? _lastOpened;

  static Future<ProgressStore> load() async => ProgressStore(await SharedPreferences.getInstance());

  bool isRead(String id) => _read.contains(id);
  bool isSaved(String id) => _saved.contains(id);
  Set<String> get saved => Set.unmodifiable(_saved);
  String? get lastOpened => _lastOpened;

  int readCount(Iterable<String> ids) => ids.where(_read.contains).length;

  Future<void> toggleRead(String id) async {
    _read.contains(id) ? _read.remove(id) : _read.add(id);
    notifyListeners();
    await _prefs.setStringList(_readKey, _read.toList());
  }

  Future<void> toggleSaved(String id) async {
    _saved.contains(id) ? _saved.remove(id) : _saved.add(id);
    notifyListeners();
    await _prefs.setStringList(_savedKey, _saved.toList());
  }

  Future<void> opened(String id) async {
    if (_lastOpened == id) return;
    _lastOpened = id;
    notifyListeners();
    await _prefs.setString(_lastKey, id);
  }
}
