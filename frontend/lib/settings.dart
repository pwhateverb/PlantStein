import 'package:flutter/material.dart';
import 'package:plant_stein/room_details.dart';

class Settings extends StatelessWidget {
  const Settings({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        body: ElevatedButton(
      child: const Text('Kitchen'),
      onPressed: () {
        Navigator.push(
            context,
            MaterialPageRoute(
                builder: (context) => const RoomDetails(1, "Kitchen")));
      },
    ));
  }
}
