use std::fs;

use enigo::{Direction, Enigo, Key, Keyboard, Mouse, Settings};

use futures::{SinkExt, StreamExt};
use serde::Deserialize;
use tokio::{
    net::{TcpListener, TcpStream},
    signal,
    sync::mpsc,
};

use tokio_tungstenite::{accept_async, tungstenite::Message};

enum ReturnButton {
    Keyboard(enigo::Key),
    Mouse(enigo::Button),
    None
}

#[derive(Debug, Deserialize)]
struct PadConfig {
    top: String,
    left: String,
    right: String,
    bottom: String,
}

#[derive(Debug, Deserialize)]
struct GameConfig {
    #[serde(rename = "1d-maze")]
    maze_1d: PadConfig,
    #[serde(rename = "custom-rooms")]
    custom_rooms: PadConfig,
    #[serde(rename = "recursed-samurai")]
    recursed_samurai: PadConfig,
}

impl GameConfig {

    fn new() -> Self {
        let file_path = "game_config.json";
        let json_content = fs::read_to_string(file_path).unwrap();

        let config: GameConfig = serde_json::from_str(&json_content).unwrap();
        config
    }

    fn get_key(self: &Self, game_index: u8, key_str: &str) -> ReturnButton {
        let current_game = match game_index {
            0 => &self.maze_1d,
            1 => &self.custom_rooms,
            2 => &self.recursed_samurai,
            _ => &self.maze_1d
        };

        let pad = match key_str {
            "top" => &current_game.top,
            "bottom" => &current_game.bottom,
            "left" => &current_game.left,
            "right" => &current_game.right,
            _ => &current_game.top,
        };

        if pad.starts_with("mouse") {
            match pad.chars().last() {
                Some('0') => ReturnButton::Mouse(enigo::Button::Left),
                Some('1') => ReturnButton::Mouse(enigo::Button::Right),
                Some(_) => ReturnButton::None,
                None => ReturnButton::None
            }
        } else {
            let line = pad.chars().collect::<Vec<char>>();
            ReturnButton::Keyboard(Key::Unicode(line[0]))
        }
    }

}

fn handle_commands(game_config: &GameConfig, command: &str) -> Option<(ReturnButton, Direction)> {
    let splitted = command.split_once("_");

    match splitted {
        None => None,
        Some(splitted) => {
            if splitted.0 != "joystick" {
                let key = game_config.get_key(2, splitted.1);

                match key {
                    ReturnButton::None => None,
                    ReturnButton::Mouse(mouse) => Some((ReturnButton::Mouse(mouse), Direction::Click)),
                    ReturnButton::Keyboard(keyboard) => {
                        match splitted.0 {
                            "press" => Some((ReturnButton::Keyboard(keyboard), Direction::Press)),
                            "release" => Some((ReturnButton::Keyboard(keyboard), Direction::Release)),
                            _ => None,
                        }
                    }
                }
            } else {
                let joystick_splitted = splitted.1.split_once("_").unwrap();
                let key = match joystick_splitted.1 {
                    "up" => Some(Key::UpArrow),
                    "down" => Some(Key::DownArrow),
                    "left" => Some(Key::LeftArrow),
                    "right" => Some(Key::RightArrow),
                    _ => None,
                };

                if key.is_none() {
                    return None;
                }

                match joystick_splitted.0 {
                    "press" => Some((ReturnButton::Keyboard(key.unwrap()), Direction::Press)),
                    "release" => Some((ReturnButton::Keyboard(key.unwrap()), Direction::Release)),
                    _ => None,
                }
            }
        }
    }
}

async fn handle_connection(tls_stream: TcpStream) {
    let ws_stream = accept_async(tls_stream).await.expect("Failed to accept");
    println!(
        "New WebSocket connection: {}",
        ws_stream.get_ref().peer_addr().unwrap()
    );

    let mut enigo = Enigo::new(&Settings::default()).unwrap();
    let game_config = GameConfig::new();

    let (mut write, mut read) = ws_stream.split();

    while let Some(msg) = read.next().await {
        match msg {
            Ok(Message::Text(text)) => {
                let text_str = text.as_str().trim();
                // println!("Received: {}", text_str);
                if text_str == "ping" {
                    let response = Message::Text("pong".into());
                    write.send(response).await.expect("Failed to send message");
                    println!("Sent: pong");
                } else {
                    match handle_commands(&game_config, text_str) {
                        Some((key, direction)) => {
                            match key {
                                ReturnButton::Keyboard(key) => {
                                    let msg = format!("Failed to {:?} {:?}", direction, key);
                                    enigo.key(key, direction).expect(msg.as_str());
                                },
                                ReturnButton::Mouse(mouse) => {
                                    let msg = format!("Failed to click mouse button {:?}", mouse);
                                    enigo.button(mouse, direction).expect(msg.as_str());
                                },
                                ReturnButton::None => {
                                    if !text_str.starts_with("joystick") {
                                        println!("Unknown message: {}", text_str);
                                    }
                                }
                            }
                        }
                        None => {
                            if !text_str.starts_with("joystick") {
                                println!("Unknown message: {}", text_str);
                            }
                        }
                    }
                }
            }
            Ok(Message::Close(_)) => {
                println!("Client disconnected");
                break;
            }
            _ => {}
        }
    }
}

#[tokio::main]
async fn main() {
    let server_info = "0.0.0.0:12345";
    let listener = TcpListener::bind(server_info)
        .await
        .expect("Could not bind");

    println!("WebSocket Secure (WSS) server listening on {}", server_info);

    let (tx, mut rx) = mpsc::channel(1);

    tokio::spawn(async move {
        signal::ctrl_c().await.expect("Failed to listen for event");
        tx.send(()).await.expect("Failed to send shutdown signal");
    });

    loop {
        tokio::select! {
            _ = rx.recv() => {
                println!("Shutting down server...");
                break;
            }
            result = listener.accept() => {
                match result {
                    Ok((stream, _)) => {
                        tokio::spawn(async move {
                            handle_connection(stream).await;
                        });
                    }
                    Err(e) => {
                        println!("Failed to accept connection: {}", e);
                    }
                }
            }
        }
    }
}
