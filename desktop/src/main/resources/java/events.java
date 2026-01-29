            @Override
			public void onClick(PlayerItem current) {
			%1$s
			}

			@Override
			public void onTouchStart(PlayerItem current,InputEvent event) {
%2$s
			}

			@Override
			public void onTouchEnd(PlayerItem current, InputEvent event) {
%3$s
			}

			@Override
			public void onBodyCreated(PlayerItem current) {
%4$s
			}

            @Override
            public void onBodyUpdate(PlayerItem current){
%5$s
            }
            
			@Override
			public void onCollisionBegin(PlayerItem current, PlayerItem body2) {
%6$s
           }
            
			@Override
			public void onCollisionEnd(PlayerItem current, PlayerItem body2) {
%7$s
			}
			
			@Override
			public String getName(){
			    %8$s
			}