import 'package:flutter/material.dart';

Route<T> liquidRoute<T>({
  required WidgetBuilder builder,
  bool animate = true,
}) {
  return PageRouteBuilder<T>(
    pageBuilder: (context, animation, secondaryAnimation) => builder(context),
    transitionDuration:
        animate ? const Duration(milliseconds: 220) : Duration.zero,
    reverseTransitionDuration:
        animate ? const Duration(milliseconds: 160) : Duration.zero,
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      if (!animate) return child;
      final curved = CurvedAnimation(
        parent: animation,
        curve: Curves.easeOutCubic,
        reverseCurve: Curves.easeInCubic,
      );
      return FadeTransition(
        opacity: curved,
        child: ScaleTransition(
          scale: Tween<double>(begin: 0.988, end: 1).animate(curved),
          child: child,
        ),
      );
    },
  );
}
