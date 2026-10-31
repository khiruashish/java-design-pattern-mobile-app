import 'package:flutter/widgets.dart';

import 'content/content_repository.dart';
import 'content/models.dart';
import 'progress/progress_store.dart';

/// Dependency injection without a package: the composition root puts these here once.
class AppScope extends InheritedWidget {
  const AppScope({
    super.key,
    required this.repository,
    required this.catalog,
    required this.progress,
    required super.child,
  });

  final ContentRepository repository;
  final Catalog catalog;
  final ProgressStore progress;

  static AppScope of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope missing above this widget');
    return scope!;
  }

  @override
  bool updateShouldNotify(AppScope oldWidget) =>
      repository != oldWidget.repository || catalog != oldWidget.catalog || progress != oldWidget.progress;
}
