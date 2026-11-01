import 'dart:convert';

import 'package:flutter/services.dart';

import 'models.dart';

/// Repository pattern: the only class that knows where content comes from.
/// Today it's bundled assets (works offline); a network-backed version can implement the same interface.
abstract interface class ContentRepository {
  Future<Catalog> catalog();
  Future<String> markdown(PageMeta page);
}

class AssetContentRepository implements ContentRepository {
  AssetContentRepository({AssetBundle? bundle}) : _bundle = bundle ?? rootBundle;

  final AssetBundle _bundle;
  Catalog? _catalog;
  final Map<String, String> _cache = {};

  @override
  Future<Catalog> catalog() async {
    return _catalog ??= Catalog.fromJson(
      jsonDecode(await _bundle.loadString('assets/content/index.json')) as Map<String, dynamic>,
    );
  }

  @override
  Future<String> markdown(PageMeta page) async {
    return _cache[page.id] ??= await _bundle.loadString('assets/content/${page.id}.md');
  }
}
