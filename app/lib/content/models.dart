/// Data the app reads from assets/content/index.json, produced by tools/build_app_content.py.
class PageMeta {
  const PageMeta({
    required this.id,
    required this.path,
    required this.title,
    required this.summary,
    required this.minutes,
    required this.written,
    required this.isIndex,
    required this.tags,
    required this.group,
  });

  factory PageMeta.fromJson(Map<String, dynamic> json) => PageMeta(
        id: json['id'] as String,
        path: json['path'] as String,
        title: json['title'] as String,
        summary: json['summary'] as String? ?? '',
        minutes: json['minutes'] as int?,
        written: json['written'] as bool? ?? false,
        isIndex: json['isIndex'] as bool? ?? false,
        tags: (json['tags'] as List<dynamic>? ?? const []).cast<String>(),
        group: json['group'] as String? ?? '',
      );

  final String id;
  final String path;
  final String title;
  final String summary;
  final int? minutes;
  final bool written;
  final bool isIndex;
  final List<String> tags;
  final String group;
}

class Level {
  const Level({
    required this.id,
    required this.code,
    required this.name,
    required this.experience,
    required this.pageIds,
  });

  factory Level.fromJson(Map<String, dynamic> json) => Level(
        id: json['id'] as String,
        code: json['code'] as String? ?? '',
        name: json['name'] as String,
        experience: json['experience'] as String? ?? '',
        pageIds: (json['pages'] as List<dynamic>).cast<String>(),
      );

  final String id;
  final String code;
  final String name;
  final String experience;
  final List<String> pageIds;
}

/// The whole table of contents, with lookups.
class Catalog {
  Catalog({required this.levels, required this.sections, required List<PageMeta> pages})
      : pages = List.unmodifiable(pages),
        _byId = {for (final p in pages) p.id: p},
        _byPath = {for (final p in pages) p.path: p};

  factory Catalog.fromJson(Map<String, dynamic> json) => Catalog(
        levels: [for (final l in json['levels'] as List<dynamic>) Level.fromJson(l as Map<String, dynamic>)],
        sections: [for (final s in json['sections'] as List<dynamic>) Level.fromJson(s as Map<String, dynamic>)],
        pages: [for (final p in json['pages'] as List<dynamic>) PageMeta.fromJson(p as Map<String, dynamic>)],
      );

  final List<Level> levels;
  final List<Level> sections;
  final List<PageMeta> pages;
  final Map<String, PageMeta> _byId;
  final Map<String, PageMeta> _byPath;

  PageMeta? byId(String id) => _byId[id];

  /// Resolves a relative Markdown link such as `../l2-practitioner/visitor.md#anchor` from [from].
  PageMeta? resolveLink(PageMeta from, String href) {
    final target = href.split('#').first;
    if (target.isEmpty || !target.endsWith('.md') || target.contains('://')) return null;
    final parts = from.path.split('/')..removeLast();
    for (final segment in target.split('/')) {
      if (segment == '..') {
        if (parts.isNotEmpty) parts.removeLast();
      } else if (segment != '.') {
        parts.add(segment);
      }
    }
    return _byPath[parts.join('/')];
  }

  /// Topic pages (not level index pages) of a level or section.
  List<PageMeta> topicsOf(Level level) =>
      [for (final id in level.pageIds) if (_byId[id] case final p? when !p.isIndex) p];

  List<PageMeta> get allTopics => [for (final l in levels) ...topicsOf(l)];
}
